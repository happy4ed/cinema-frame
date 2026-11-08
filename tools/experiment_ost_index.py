"""Read-only 200-film OST indexing experiment. No Android selection changes."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from collections import Counter
import hashlib
import json
from pathlib import Path
import time
import threading
import urllib.parse
import urllib.request
import urllib.error
from compare_tag_flows import Client, Rules, ROOT, TTL, save, album_matches, album_tracks

FOLDER=ROOT/'work/ost-index-experiment'
MODEL='gemini-2.5-flash'
STYLES=['jazz','classical','rock','pop','electronic','orchestral_score','other']
MOODS=['epic','calm','upbeat']

def tags_from(data):
    tags=data.get('toptags',{}).get('tag',[])
    if isinstance(tags,dict):tags=[tags]
    return [t for t in tags if t.get('name') and ('count' not in t or float(t['count'])>0)]

def classify_tags(tags,rules):
    # Jazz/mood synonyms are the unchanged user-editable dictionary. Other style
    # buckets use only the literal names requested by the user in this experiment.
    groups={'jazz':rules.data['genres']['jazz'],'classical':['classical'],'rock':['rock'],
            'pop':['pop'],'electronic':['electronic'],'orchestral_score':['orchestral','orchestral score']}
    values={rules.canonical(t['name']) for t in tags}
    return {'styles':[name for name,words in groups.items() if values.intersection(rules.canonical(w) for w in words)],
            'moods':[name for name,words in rules.data['moods'].items() if values.intersection(rules.canonical(w) for w in words)]}

def films():
    data=json.loads((ROOT/'android/assets/movies.json').read_text(encoding='utf-8'))['movies']
    # Retain the user's persistent India exclusion. No genre/style/era restriction.
    return sorted((m for m in data if 'IN' not in m.get('countries',[])),key=lambda m:(-m['votes'],-m['rating'],m['id']))[:200]

class Tmdb:
    def __init__(self):
        self.key=(ROOT/'work/tmdb-api-key.txt').read_text(encoding='utf-8-sig').strip()
        self.lock=threading.Lock();self.last=0;self.events=[];self.hits=0
    def get(self,path):
        cache=FOLDER/'tmdb-cache'/(hashlib.sha256(path.encode()).hexdigest()+'.json')
        if cache.exists():
            d=json.loads(cache.read_text(encoding='utf-8'))
            if 0<=time.time()-d['fetched_at']<TTL:
                with self.lock:self.hits+=1
                return d['response']
        with self.lock:
            time.sleep(max(0,.3-(time.monotonic()-self.last)));self.last=time.monotonic()
            event={'path':path,'started_at':time.time()};self.events.append(event)
        url='https://api.themoviedb.org/3/'+path;headers={}
        if self.key.startswith('eyJ'):headers['Authorization']='Bearer '+self.key
        else:url+=('&' if '?' in path else '?')+urllib.parse.urlencode({'api_key':self.key})
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers=headers),timeout=25) as response:
                data=json.load(response);event['http']=response.status
        except urllib.error.HTTPError as exc:
            event['http']=exc.code;raise RuntimeError('TMDB HTTP '+str(exc.code)) from None
        save(cache,{'source':'TMDB','path':path,'fetched_at':time.time(),'expires_at':time.time()+TTL,'response':data})
        return data

def composer_matches(name,composers):
    from compare_tag_flows import norm
    return any(norm(c)==norm(name) or norm(c) in [norm(x) for x in name.split(',')] for c in composers)

def collect_movie(client,tmdb,movie):
    record={'movie':movie,'tracks':[],'fetched_at':time.time()}
    found=tmdb.get('find/'+movie['id']+'?external_source=imdb_id').get('movie_results',[])
    if len(found)!=1:return dict(record,skip='no_unique_tmdb_imdb_movie')
    credits=tmdb.get('movie/'+str(found[0]['id'])+'/credits')
    composers=sorted({x['name'] for x in credits.get('crew',[]) if x.get('job')=='Original Music Composer'})
    record['credited_composers']=composers;record['album_checks']=[]
    if not composers:return dict(record,skip='no_tmdb_composer_evidence')
    album=None
    queries=[movie['originalTitle']+' soundtrack',movie['originalTitle']+' '+composers[0]]
    checked=set()
    for query in queries:
        hits=client.get('deezer','search/album',{'q':query,'limit':100,'order':'RANKING'})
        for a in hits.get('data',[]):
            if a['id'] in checked:continue
            checked.add(a['id'])
            if not album_matches(movie,a['title']):continue
            low=a['title'].lower()
            if any(word in low for word in ('live','cover','tribute','piano version','orchestra version','re-record')):
                record['album_checks'].append({'id':a['id'],'title':a['title'],'skip':'alternate_performance'});continue
            artist=a.get('artist',{}).get('name','')
            verified=composer_matches(artist,composers)
            if not verified and artist.lower() in ('various artists','various'):
                members=album_tracks(client,a['id'])
                verified=any(composer_matches(t.get('artist',{}).get('name',''),composers) for t in members)
            record['album_checks'].append({'id':a['id'],'title':a['title'],'artist':artist,'composer_link_verified':verified})
            if verified:album=a;break
        if album:break
    if not album:return dict(record,skip='no_ost_album_with_exact_title_and_credited_composer')
    record['album']={k:album.get(k) for k in ('id','title','artist')}
    record['album_evidence']='Exact film title + original soundtrack designation + TMDB IMDb-linked original music composer. Not independent publisher certification.'
    for t in album_tracks(client,album['id']):
        if 'isrc' not in t or 'rank' not in t:t=client.get('deezer','track/'+str(t['id']))
        if not isinstance(t.get('rank'),(int,float)):raise ValueError('Deezer rank missing; stop')
        record['tracks'].append({'id':str(t['id']),'title':t['title'],'artist':t.get('artist',{}).get('name',''),
            'artist_id':t.get('artist',{}).get('id'),'rank':t['rank'],'isrc':t.get('isrc',''),
            'movie_id':movie['id'],'movie_title':movie['title'],'album_id':album['id'],'fetched_at':time.time()})
    return record

def validate_judgments(value,rows):
    decisions=value.get('decisions')
    if isinstance(decisions,dict):decisions=[{'key':key,**value} for key,value in decisions.items()]
    if not isinstance(decisions,list):raise ValueError('Gemini decisions missing')
    expected={r['key'] for r in rows};found=set()
    for d in decisions:
        key=d.get('key')
        if key not in expected or key in found:raise ValueError('Gemini extra/duplicate key')
        found.add(key)
        if d.get('knowledge') not in ('known','unknown'):raise ValueError('Gemini invalid knowledge')
        if not isinstance(d.get('reason'),str) or not d['reason'].strip():raise ValueError('Gemini reason missing')
        for field,allowed in [('styles',STYLES),('moods',MOODS)]:
            if not isinstance(d.get(field),list) or any(x not in allowed for x in d[field]):raise ValueError('Gemini invalid '+field)
        if d['knowledge']=='unknown' and (d['styles'] or d['moods']):raise ValueError('Unknown Gemini judgment carries labels')
    if found!=expected:raise ValueError('Gemini omitted input tracks')
    return decisions

def gemini(rows,purpose,key,state):
    prompt=('Classify the specific soundtrack recordings below, not the film genre or the artist in general. '
        'Use only knowledge you actually have about this recording. Do not infer a genre or mood just from its title, '
        'film genre, or performer reputation. If you cannot identify the recording confidently, set knowledge=unknown, '
        'styles=[], moods=[], and explain that you do not know. For a known recording return all fitting styles and moods. '
        'Jazz includes bebop, swing, big band, hard bop, cool/vocal/smooth/latin jazz and jazz fusion. '
        'epic=grand/majestic, calm=quiet/rainy/relaxed, upbeat=cheerful/lively. '
        'Return decisions as an array with exactly one item per input key, each key used once, with a short Korean reason for each. Do not add or omit keys.\n'+json.dumps(rows,ensure_ascii=False))
    item_schema={'type':'OBJECT','properties':{
        'knowledge':{'type':'STRING','enum':['known','unknown']},
        'styles':{'type':'ARRAY','items':{'type':'STRING','enum':STYLES}},
        'moods':{'type':'ARRAY','items':{'type':'STRING','enum':MOODS}},'reason':{'type':'STRING'}},
        'required':['knowledge','styles','moods','reason']}
    item_schema['properties']['key']={'type':'STRING','enum':[r['key'] for r in rows]}
    item_schema['required'].append('key')
    schema={'type':'OBJECT','properties':{'decisions':{'type':'ARRAY','items':item_schema}},'required':['decisions']}
    body={'contents':[{'parts':[{'text':prompt}]}],'generationConfig':{'temperature':0,'responseMimeType':'application/json','responseSchema':schema}}
    digest=hashlib.sha256(json.dumps([MODEL,body],sort_keys=True,ensure_ascii=False).encode()).hexdigest()
    cache=FOLDER/'gemini-cache'/(digest+'.json')
    if cache.exists():
        saved=json.loads(cache.read_text(encoding='utf-8'))
        if 0<=time.time()-saved['fetched_at']<TTL:
            state['gemini_cache_hits']+=1
            return validate_judgments(saved['parsed'],rows)
    deadline=state.get('gemini_not_before',0)
    while time.time()<deadline:time.sleep(min(10,deadline-time.time()))
    started=time.time();state['gemini_not_before']=started+15
    event={'purpose':purpose,'model':MODEL,'started_at':started,'input_count':len(rows),'cache_key':digest}
    state['gemini_events'].append(event);save(FOLDER/'checkpoint.json',state)
    req=urllib.request.Request('https://generativelanguage.googleapis.com/v1beta/models/'+MODEL+':generateContent',
        data=json.dumps(body).encode(),headers={'x-goog-api-key':key,'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=120) as response:raw=json.load(response);event['http']=response.status
    except urllib.error.HTTPError as exc:
        event.update(http=exc.code,retry_after=exc.headers.get('Retry-After'))
        # Persist the provider error for diagnosis; redact the key before writing.
        event['error_body']=exc.read().decode(errors='replace').replace(key,'[REDACTED]')[:4000]
        raise RuntimeError('Gemini HTTP '+str(exc.code)+'; stopped') from None
    save(FOLDER/'gemini-responses'/(digest+'.json'),{'source':'Gemini 판단','input':rows,'response':raw,'fetched_at':time.time()})
    parts=raw['candidates'][0]['content']['parts'];text=''.join(p.get('text','') for p in parts if not p.get('thought'))
    def no_duplicate_keys(pairs):
        result={}
        for k,v in pairs:
            if k in result:raise ValueError('Gemini duplicate JSON property')
            result[k]=v
        return result
    parsed=json.loads(text,object_pairs_hook=no_duplicate_keys);decisions=validate_judgments(parsed,rows)
    event['elapsed_seconds']=time.time()-started
    save(cache,{'source':'Gemini 판단','model':MODEL,'purpose':purpose,'fetched_at':time.time(),'expires_at':time.time()+TTL,
                'input':rows,'parsed':parsed,'response':raw})
    return decisions

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--metadata-only',action='store_true');args=parser.parse_args()
    FOLDER.mkdir(parents=True,exist_ok=True)
    key=(ROOT/'work/lastfm-api-key.txt').read_text(encoding='utf-8-sig').strip()
    client=Client(FOLDER,key,'');tmdb=Tmdb();rules=Rules(ROOT/'config/music-tags.json');started=time.time()
    cp=FOLDER/'checkpoint.json'
    state=json.loads(cp.read_text(encoding='utf-8')) if cp.exists() else {'movies':[],'artist_tags':{},'track_tags':{},'gemini':{},'gemini_events':[],'gemini_cache_hits':0,'runs':[]}
    if state.get('runs') and started-state['runs'][0]['started_at']>=TTL:
        save(FOLDER/'expired-checkpoint.json',state)
        state={'movies':[],'artist_tags':{},'track_tags':{},'gemini':{},'gemini_events':[],'gemini_cache_hits':0,'runs':[]}
    run={'started_at':started};state['runs'].append(run)
    try:
        selected=films();save(FOLDER/'selected-films.json',selected)
        done={r['movie']['id'] for r in state['movies']};remaining=[m for m in selected if m['id'] not in done]
        with ThreadPoolExecutor(max_workers=4) as pool:
            for start in range(0,len(remaining),4):
                state['movies'].extend(pool.map(lambda m:collect_movie(client,tmdb,m),remaining[start:start+4]));save(cp,state)
                print('MOVIES',len(state['movies']),'TRACK_ROWS',sum(len(m['tracks']) for m in state['movies']),flush=True)
        tracks={}
        for m in state['movies']:
            for t in m['tracks']:
                # Same Deezer recording in multiple films is one song; retain all film links.
                if t['id'] not in tracks:tracks[t['id']]={**t,'movies':[]}
                tracks[t['id']]['movies'].append({'id':t['movie_id'],'title':t['movie_title']})
        artists=sorted({t['artist'] for t in tracks.values() if t['artist']})
        def artist_fetch(name):return name,tags_from(client.get('lastfm','',{'method':'artist.getTopTags','artist':name,'autocorrect':0}))
        remaining=[a for a in artists if a not in state['artist_tags']]
        with ThreadPoolExecutor(max_workers=4) as pool:
            for start in range(0,len(remaining),20):
                state['artist_tags'].update(pool.map(artist_fetch,remaining[start:start+20]));save(cp,state)
                print('ARTISTS',len(state['artist_tags']),'/',len(artists),flush=True)
        def track_fetch(t):return t['id'],tags_from(client.get('lastfm','',{'method':'track.getTopTags','artist':t['artist'],'track':t['title'],'autocorrect':0}))
        remaining=[t for t in tracks.values() if t['id'] not in state['track_tags']]
        with ThreadPoolExecutor(max_workers=4) as pool:
            for start in range(0,len(remaining),40):
                state['track_tags'].update(pool.map(track_fetch,remaining[start:start+40]));save(cp,state)
                print('TRACK_TAGS',len(state['track_tags']),'/',len(tracks),flush=True)
        rows=[]
        for t in tracks.values():
            at=state['artist_tags'].get(t['artist'],[]);tt=state['track_tags'][t['id']]
            a=classify_tags(at,rules);b=classify_tags(tt,rules)
            t.update(artist_tags=at,track_tags=tt,artist_classification=a,track_classification=b,
                     tag_styles=a['styles'] or b['styles'],tag_moods=a['moods'] or b['moods'])
            rows.append(t)
        save(FOLDER/'tag-stage.json',{'tracks':rows})
        if not args.metadata_only:
            gemini_key=(ROOT/'work/gemini-api-key.txt').read_text(encoding='utf-8-sig').strip()
            pending=[t for t in rows if (not t['tag_styles'] or not t['tag_moods']) and t['id'] not in state['gemini']]
            def inputs(batch):return [{'key':t['id'],'track':t['title'],'artist':t['artist'],'films':t['movies']} for t in batch]
            for start in range(0,len(pending),30):
                for d in gemini(inputs(pending[start:start+30]),'unresolved_style_or_mood',gemini_key,state):state['gemini'][d['key']]=d
                save(cp,state);print('GEMINI',len(state['gemini']),'batch',start//30+1,flush=True)
            # Blind, deterministic sample: 25 jazz-tag songs + 25 other classified
            # songs where possible. Artist tags are NOT sent to Gemini.
            comparable=[t for t in rows if t['artist_classification']['styles']]
            order=lambda t:hashlib.sha256(('artist-validation-v1:'+t['id']).encode()).hexdigest()
            jazz=sorted((t for t in comparable if 'jazz' in t['artist_classification']['styles']),key=order)
            others=sorted((t for t in comparable if 'jazz' not in t['artist_classification']['styles']),key=order)
            sample=jazz[:25]+others[:25]
            used={t['id'] for t in sample};sample+=sorted((t for t in comparable if t['id'] not in used),key=order)[:50-len(sample)]
            judgments=gemini(inputs(sample),'blind_artist_tag_agreement_50',gemini_key,state) if sample else []
            state['validation']={'sample':sample,'judgments':judgments};save(cp,state)
        for t in rows:
            d=state['gemini'].get(t['id']);t['gemini']=d
            t['final_styles']=t['tag_styles'] or (d['styles'] if d and d['knowledge']=='known' else [])
            t['final_moods']=t['tag_moods'] or (d['moods'] if d and d['knowledge']=='known' else [])
        summary={'complete':not args.metadata_only,'movies':len(state['movies']),'albums_found':sum('album' in m for m in state['movies']),
            'track_rows':sum(len(m['tracks']) for m in state['movies']),'unique_deezer_tracks':len(rows),'unique_isrcs':len({t['isrc'] for t in rows if t['isrc']}),
            'artists':len(artists),'artists_with_tags':sum(bool(state['artist_tags'].get(a)) for a in artists),
            'tracks_with_artist_tags':sum(bool(t['artist_tags']) for t in rows),'artist_tag_jazz':sum('jazz' in t['artist_classification']['styles'] for t in rows),
            'tag_stage_jazz':sum('jazz' in t['tag_styles'] for t in rows),'after_gemini_jazz':sum('jazz' in t['final_styles'] for t in rows),
            'gemini_unknown':sum(d['knowledge']=='unknown' for d in state['gemini'].values()),'tracks':rows}
        save(FOLDER/'result.json',summary);print('RESULT',json.dumps({k:v for k,v in summary.items() if k!='tracks'}),flush=True)
        run['complete']=True
    except Exception as exc:
        run.update(complete=False,error=str(exc));print('STOP',str(exc),flush=True);raise
    finally:
        run.update(elapsed_seconds=time.time()-started,counts=dict(client.counts),events=client.events,tmdb_events=tmdb.events,tmdb_cache_hits=tmdb.hits)
        save(cp,state)

if __name__=='__main__':main()
