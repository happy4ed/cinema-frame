"""Actual alternative album search; preserves uncertainty instead of forced links."""
import json,re,time,hashlib,argparse
from pathlib import Path
from compare_tag_flows import Client,ROOT,save,norm,album_tracks
from experiment_ost_index import Tmdb,composer_matches

OUT=ROOT/'work/album-recovery'
def title_core(value):
    value=re.sub(r'\bepisode\s+(?:[ivx]+|[0-9]+)\b','',value,flags=re.I)
    value=re.split(r'[\[(]',value,1)[0]
    value=re.sub(r'^(?:original\s+)?(?:motion picture|film)\s+(?:soundtrack|score)\s*[:/-]?\s*','',value,flags=re.I)
    value=re.sub(r'(?:\s*[:/–-]\s*(?:original\s+)?(?:motion picture\s+)?(?:soundtrack|score)|\s+(?:original\s+(?:motion picture\s+)?(?:soundtrack|score)|motion picture\s+(?:soundtrack|score)|soundtrack|music from|music composed|bande originale))\b.*$', '',value,flags=re.I)
    return norm(value)

def title_match(titles,album):
    if re.search(r'\b(live|cover|tribute|karaoke|reimagined|suite|escapades|arranged|solo piano|cinema classics)\b|\barr\.',album,re.I):return False
    core=title_core(album)
    return bool(core) and any(core==title_core(title) for title in titles)

def release_check(movie,spotify,deezer):
    if spotify.get('album_type')=='single' or deezer.get('record_type')=='single':return 'single_not_ost_album'
    if not re.search(r'soundtrack|motion picture|original score|music from|bande originale|original music',deezer.get('title',''),re.I):
        years={int(x) for c in spotify.get('copyrights',[]) for x in re.findall(r'\b(?:19|20)[0-9]{2}\b',c.get('text',''))}
        date=spotify.get('release_date','')
        if len(date)>=4 and date[:4].isdigit():years.add(int(date[:4]))
        if not any(abs(year-movie['year'])<=1 for year in years):return 'bare_title_original_release_year_unverified'
    return None

def main():
    global OUT
    parser=argparse.ArgumentParser();parser.add_argument('--expanded',action='store_true');parser.add_argument('--strict',action='store_true');args=parser.parse_args()
    if args.expanded:OUT=ROOT/'work/album-recovery-expanded'
    if args.strict:args.expanded=True;OUT=ROOT/'work/album-recovery-strict'
    cookie=Path(r'C:\Users\happy\Documents\Codex\2026-11-01\new-chat\work\controller-session.txt').read_text(encoding='utf-8-sig').strip()
    client=Client(OUT,'',cookie);tmdb=Tmdb()
    baseline=json.loads((ROOT/(('work/album-recovery-expanded/checkpoint.json' if args.strict else 'work/album-recovery/checkpoint.json') if args.expanded else 'work/ost-index-experiment/checkpoint.json')).read_text(encoding='utf-8'))['records' if args.expanded else 'movies']
    cp=OUT/'checkpoint.json';state=json.loads(cp.read_text(encoding='utf-8')) if cp.exists() else {'records':[],'runs':[]}
    done={x['movie']['id'] for x in state['records']};run={'started_at':time.time()};state['runs'].append(run)
    def spot(body):time.sleep(1);return client.get('spotify','api/music/search',post=body)
    try:
        for old in baseline:
            m=old['movie']
            if m['id'] in done:continue
            r={'movie':m,'baseline_album':old.get('album'),'baseline_skip':old.get('skip'),'credited_composers':old.get('credited_composers',[]),'attempts':[]}
            retain=bool(old.get('album'))
            if retain and args.strict and old.get('spotify_album'):
                sp=old['spotify_album'];detail=spot({'album_id':sp['id']});dz=client.get('deezer','album/'+str(old['album']['id']))
                retain=title_match([m['title'],m['originalTitle']],dz['title']) and not release_check(m,detail,dz)
                r['prior_link_rejected']=not retain
            if retain:
                r.update(album=old['album'],method=old.get('method','baseline'),tracks=old['tracks']);r.update({k:old[k] for k in ('spotify_album','matched_query','recovery_reason') if k in old});state['records'].append(r);continue
            composers=r['credited_composers']
            if not composers:
                r['reason']='other_missing_music_credit';state['records'].append(r);save(cp,state);continue
            found=tmdb.get('find/'+m['id']+'?external_source=imdb_id')['movie_results']
            aliases=list(composers)
            if args.expanded and found:
                crew=tmdb.get('movie/'+str(found[0]['id'])+'/credits').get('crew',[])
                for person in crew:
                    if person.get('job')=='Original Music Composer':
                        detail_person=tmdb.get('person/'+str(person['id']))
                        aliases+=detail_person.get('also_known_as',[])
                r['credit_aliases']=list(dict.fromkeys(aliases))
            def credit_matches(name):
                if not args.expanded:return composer_matches(name,composers)
                a=norm(re.sub(r'^(the|a|an)\s+','',name,flags=re.I))
                for candidate in aliases:
                    b=norm(re.sub(r'^(the|a|an)\s+','',candidate,flags=re.I))
                    if a==b or (len(b)>=8 and b in a):return True
                return False
            titles=list(dict.fromkeys([m['title'],m['originalTitle']]+[t for f in found for t in (f.get('title'),f.get('original_title')) if t]))
            queries=['album:"'+t.replace('"','')+'"' for t in titles]
            if args.strict:queries=['album:"'+t.replace('"','')+'" soundtrack' for t in titles]+queries
            queries+=['album:"'+titles[0].replace('"','')+'" year:'+str(m['year']),titles[0]+' '+composers[0]+' soundtrack']
            if args.expanded:
                if args.strict:queries=['album:"'+t.replace('"','')+'" soundtrack' for t in titles]+queries
            queries+=['album:"'+t.replace('"','')+'" artist:"'+c.replace('"','')+'"' for t in titles[:2] for c in composers[:2]]
            seen=set();accepted=False;spotify_candidates=0
            for q in queries:
                hits=spot({'album_query':q}).get('albums',[])
                r['attempts'].append({'query':q,'returned':len(hits)})
                for hit in hits:
                    if hit['id'] in seen:continue
                    seen.add(hit['id'])
                    if not title_match(titles,hit['name']):continue
                    artist_names=[a['name'] for a in hit.get('artists',[])]
                    detail=None
                    verified=any(credit_matches(a) for a in artist_names)
                    if not verified and args.expanded and any(a.lower() in ('various artists','various') for a in artist_names):
                        detail=spot({'album_id':hit['id']})
                        verified=any(credit_matches(a['name']) for t in detail.get('tracks',{}).get('items',[]) for a in t.get('artists',[]))
                    if not verified:
                        r['attempts'].append({'spotify_id':hit['id'],'title':hit['name'],'artists':artist_names,'skip':'composer_mismatch'});continue
                    spotify_candidates+=1
                    detail=detail or spot({'album_id':hit['id']});upc=detail.get('external_ids',{}).get('upc')
                    if not upc:r['attempts'].append({'spotify_id':hit['id'],'skip':'spotify_upc_missing'});continue
                    dz=client.get('deezer','album/upc:'+upc)
                    if 'error' in dz:r['attempts'].append({'spotify_id':hit['id'],'upc':upc,'skip':'deezer_upc_not_found'});continue
                    rejected=release_check(m,detail,dz) if args.strict else None
                    if rejected:r['attempts'].append({'spotify_id':hit['id'],'deezer_id':dz.get('id'),'skip':rejected});continue
                    if not title_match(titles,dz.get('title','')):
                        r['attempts'].append({'spotify_id':hit['id'],'upc':upc,'deezer_id':dz.get('id'),'title':dz.get('title'),'skip':'cross_provider_title_mismatch'});continue
                    if str(dz.get('upc','')).lstrip('0')!=str(upc).lstrip('0'):
                        r['attempts'].append({'spotify_id':hit['id'],'skip':'upc_mismatch'});continue
                    year=int(str(dz.get('release_date','0000'))[:4])
                    if year and year<m['year']-1:
                        r['attempts'].append({'spotify_id':hit['id'],'deezer_year':year,'skip':'album_predates_movie'});continue
                    tracks=[]
                    for t in album_tracks(client,dz['id']):
                        if 'rank' not in t or 'isrc' not in t:t=client.get('deezer','track/'+str(t['id']))
                        if not isinstance(t.get('rank'),(int,float)):raise ValueError('Deezer rank missing')
                        tracks.append({'id':str(t['id']),'title':t['title'],'artist':t.get('artist',{}).get('name',''),'rank':t['rank'],'isrc':t.get('isrc',''),'movie_id':m['id'],'movie_title':m['title'],'album_id':dz['id'],'fetched_at':time.time()})
                    r.update(album={'id':dz['id'],'title':dz['title'],'artist':dz.get('artist'), 'upc':dz.get('upc'),'release_date':dz.get('release_date')},
                        spotify_album={'id':hit['id'],'title':hit['name'],'artists':artist_names,'upc':upc,'release_date':detail.get('release_date')},
                        tracks=tracks,method='spotify_title_composer_plus_exact_upc',matched_query=q)
                    low=dz['title'].lower()
                    r['recovery_reason']=('music_from_inspired_by_designation' if 'music from' in low or 'inspired by' in low else
                        'original_or_translated_title' if norm(titles[0]) not in norm(dz['title']) else
                        'title_designation_mismatch' if not re.search('original soundtrack|original motion picture|original score',low) else 'provider_search_recall')
                    accepted=True;break
                if accepted:break
            if not accepted:r['reason']='deezer_release_unavailable_or_unverified' if spotify_candidates else 'no_verified_album_in_search_results'
            state['records'].append(r);save(cp,state)
            print('FILMS',len(state['records']),'LINKED',sum('album' in x for x in state['records']),m['title'],r.get('recovery_reason',r.get('reason')),flush=True)
        state['complete']=True
    except Exception as exc:run['error']=str(exc);raise
    finally:
        run.update(elapsed_seconds=time.time()-run['started_at'],counts=dict(client.counts),events=client.events)
        save(cp,state)

if __name__=='__main__':main()
