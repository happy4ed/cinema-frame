"""Resumable, <=1 request/minute Wikidata P495/P345 country collector."""
import argparse
from contextlib import contextmanager
import datetime as dt
import email.utils
import json
import os
from pathlib import Path
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

COUNTRIES = dict(zip(
    'IN HK KR JP CN TW TH ID US CA MX GB FR DE IT ES RU TR IR EG NG BR AR AU'.split(),
    'Q668 Q8646 Q884 Q17 Q148 Q865 Q869 Q252 Q30 Q16 Q96 Q145 Q142 Q183 Q38 Q29 Q159 Q43 Q794 Q79 Q1033 Q155 Q414 Q408'.split()))
USER_AGENT = 'CinemaFrame/0.6 (https://github.com/happy4ed/cinema-frame/issues; country metadata)'

def atomic(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    temp.replace(path)

def retry_delay(value, now, fallback=61):
    try:
        return max(61, float(value))
    except (TypeError, ValueError):
        try:
            return max(61, email.utils.parsedate_to_datetime(value).timestamp()-now)
        except (TypeError, ValueError, OverflowError):
            return fallback

@contextmanager
def collector_lock(checkpoint):
    """OS lock is released even after a crash; prevents two workers violating pacing."""
    path=Path(checkpoint).with_suffix('.lock');path.parent.mkdir(parents=True,exist_ok=True)
    with path.open('a+b') as handle:
        if path.stat().st_size==0:handle.write(b'0');handle.flush()
        handle.seek(0)
        if os.name=='nt':
            import msvcrt
            msvcrt.locking(handle.fileno(),msvcrt.LK_NBLCK,1)
        else:
            import fcntl
            fcntl.flock(handle,fcntl.LOCK_EX|fcntl.LOCK_NB)
        try:yield
        finally:
            handle.seek(0)
            if os.name=='nt':msvcrt.locking(handle.fileno(),msvcrt.LK_UNLCK,1)
            else:fcntl.flock(handle,fcntl.LOCK_UN)

def collect(checkpoint, page_size=50000):
    with collector_lock(checkpoint):return _collect(checkpoint,page_size)

def _collect(checkpoint, page_size=50000):
    checkpoint = Path(checkpoint)
    state = json.loads(checkpoint.read_text(encoding='utf-8')) if checkpoint.exists() else {
        'schema': 1, 'user_agent': USER_AGENT, 'not_before': 0, 'countries': {}, 'requests': []}
    for code, entity in COUNTRIES.items():
        country = state['countries'].setdefault(code, {'entity': entity, 'offset': 0, 'page_size': page_size, 'pages': [], 'complete': False})
        while not country['complete']:
            while time.time() < state['not_before']:
                time.sleep(min(20, state['not_before']-time.time()))
            offset, limit = country['offset'], country['page_size']
            query = f'SELECT DISTINCT ?imdb WHERE {{ ?film wdt:P495 wd:{entity}; wdt:P345 ?imdb. }} ORDER BY ?imdb LIMIT {limit} OFFSET {offset}'
            url = 'https://query.wikidata.org/sparql?' + urllib.parse.urlencode({'query': query, 'format': 'json'})
            started = time.time()
            event = {'country': code, 'offset': offset, 'limit': limit, 'started_at': started, 'query': query}
            state['not_before'] = started + 61
            state['requests'].append(event)
            atomic(checkpoint, state)  # A crash cannot cause an immediate duplicate request.
            print(f'GET {code} OFFSET {offset} LIMIT {limit}', flush=True)
            try:
                request = urllib.request.Request(url, headers={'User-Agent': USER_AGENT, 'Accept': 'application/sparql-results+json'})
                with urllib.request.urlopen(request, timeout=70) as response:
                    raw = response.read()
                data = json.loads(raw)
                received = [r['imdb']['value'] for r in data['results']['bindings']]
                ids = [i for i in received if i.startswith('tt') and i[2:].isdigit()]
                valid_ids = set(ids)
                rejected = [i for i in received if i not in valid_ids]
                page_path = checkpoint.parent / 'pages' / f'{code}-{offset:08d}.json'
                atomic(page_path, {'country': code, 'entity': entity, 'query': query, 'fetched_at': dt.datetime.now(dt.timezone.utc).isoformat(), 'imdb_ids': ids, 'rejected_non_title_ids': rejected})
                country['pages'].append({'path': str(page_path.resolve()), 'offset': offset, 'count': len(ids)})
                country['offset'] += len(received)
                country['complete'] = len(received) < limit
                event.update(status=200, count=len(ids), rejected=len(rejected), finished_at=time.time())
                print(f'OK {code}: page {len(ids)}, total {country["offset"]}, complete={country["complete"]}', flush=True)
            except urllib.error.HTTPError as exc:
                event.update(status=exc.code, error=str(exc), retry_after=exc.headers.get('Retry-After'))
                if exc.code not in (429, 500, 502, 503, 504):
                    atomic(checkpoint, state)
                    raise
                state['not_before'] = max(state['not_before'], time.time()+retry_delay(exc.headers.get('Retry-After'), time.time()))
                if exc.code in (500, 502, 503, 504): country['page_size'] = max(1000, limit//2)
                print(f'RETRY {code}: HTTP {exc.code}; wait {int(state["not_before"]-time.time())}s', flush=True)
            except (TimeoutError, socket.timeout, urllib.error.URLError, json.JSONDecodeError) as exc:
                event.update(status='timeout_or_transport', error=str(exc)[:200])
                state['not_before'] = max(state['not_before'], time.time()+61)
                country['page_size'] = max(1000, limit//2)
                print(f'RETRY {code}: {type(exc).__name__}; checkpoint retained', flush=True)
            atomic(checkpoint, state)
    return state

def enrich(movies, state):
    if set(state['countries']) != set(COUNTRIES) or not all(c['complete'] for c in state['countries'].values()):
        raise ValueError('Country collection incomplete; catalogue must not be replaced')
    membership = {}
    for code, country in state['countries'].items():
        for page in country['pages']:
            for imdb in json.loads(Path(page['path']).read_text(encoding='utf-8'))['imdb_ids']:
                membership.setdefault(imdb, set()).add(code)
    counts = {code: 0 for code in COUNTRIES}
    for movie in movies:
        movie['countries'] = sorted(membership.get(movie['id'], set()))
        for code in movie['countries']: counts[code] += 1
    known = sum(bool(m['countries']) for m in movies)
    return {'complete': True, 'source': 'Wikidata P495/P345; requested 24-country scope',
            'fetched_at': dt.datetime.now(dt.timezone.utc).isoformat(), 'total': len(movies),
            'mapped': known, 'unknown': len(movies)-known, 'by_country': counts,
            'unknown_definition': 'No match among the requested 24 countries; may have another origin country'}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkpoint', default='work/wikidata-country-checkpoint.json')
    parser.add_argument('--page-size', type=int, default=50000)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    state = collect(args.checkpoint, args.page_size)
    if args.apply:
        project = Path(__file__).resolve().parents[1]
        payload = json.loads((project/'android/assets/movies.json').read_text(encoding='utf-8'))
        payload['country_mapping'] = enrich(payload['movies'], state)
        for relative in ('android/assets/movies.json', 'data/movies.json'):
            atomic(project/relative, payload)
        atomic(project/'work/country-mapping-statistics.json', payload['country_mapping'])
        print(json.dumps(payload['country_mapping'], ensure_ascii=False), flush=True)

if __name__ == '__main__': main()
