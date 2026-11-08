"""Read-only real API comparison; never changes Android selection or playback."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
import threading
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
UA='CinemaFrame/0.7 (https://github.com/happy4ed/cinema-frame/issues; flow comparison)'
TTL=7*86400

def save(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_suffix('.'+str(threading.get_ident())+'.tmp');temp.write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf-8');temp.replace(path)
def norm(text):return ''.join(c for c in unicodedata.normalize('NFKD',text).casefold() if c.isalnum())
def tag_norm(text):return re.sub(r'\s+',' ',re.sub(r'[_\-/]+',' ',unicodedata.normalize('NFKC',text).casefold())).strip()

class Rules:
    def __init__(self,path):
        self.data=json.loads(Path(path).read_text(encoding='utf-8'))
        self.aliases={tag_norm(k):tag_norm(v) for k,v in self.data['aliases'].items()}
    def canonical(self,tag):
        value=tag_norm(tag);return self.aliases.get(value,value)
    def groups(self,case):
        return ([self.data['genres']['jazz'],self.data['moods']['calm']] if case=='rainy-jazz' else [self.data['moods']['epic']])
    def matches(self,tags,case):
        actual={self.canonical(t) for t in tags}
        return all(actual.intersection(self.canonical(t) for t in group) for group in self.groups(case))

class Client:
    def __init__(self,folder,key,cookie):
        self.folder=folder;self.key=key;self.cookie=cookie;self.counts=Counter();self.events=[]
        self.lock=threading.Lock();self.last={};self.stop=threading.Event()
    def get(self,provider,path,params=None,post=None):
        if self.stop.is_set():raise RuntimeError('Experiment stopped by provider error')
        params=params or {};identity=json.dumps([provider,path,params,post],sort_keys=True,ensure_ascii=False)
        digest=hashlib.sha256(identity.encode()).hexdigest();cache=self.folder/'cache'/(digest+'.json')
        if cache.exists():
            value=json.loads(cache.read_text(encoding='utf-8'));age=time.time()-value['fetched_at']
            if 0<=age<TTL:
                with self.lock:self.counts[provider+'_cache_hits']+=1
                return value['response']
        with self.lock:
            delay={'lastfm':.25,'deezer':.25,'musicbrainz':1.1,'spotify':.3}[provider]
            time.sleep(max(0,delay-(time.monotonic()-self.last.get(provider,0))))
            self.last[provider]=time.monotonic();started=time.time();self.counts[provider+'_http']+=1
            event={'provider':provider,'path':path,'params':params,'post':post,'started_at':started};self.events.append(event)
        bases={'lastfm':'https://ws.audioscrobbler.com/2.0/','deezer':'https://api.deezer.com/','musicbrainz':'https://musicbrainz.org/ws/2/','spotify':'http://127.0.0.1:28787/'}
        query=dict(params)
        if provider=='lastfm':query.update(api_key=self.key,format='json')
        url=bases[provider]+path+('?' + urllib.parse.urlencode(query) if query else '')
        headers={'User-Agent':UA}
        if provider=='spotify':headers.update(Cookie='cf_session='+self.cookie,**{'Content-Type':'application/json'})
        request=urllib.request.Request(url,data=json.dumps(post).encode() if post else None,headers=headers)
        try:
            with urllib.request.urlopen(request,timeout=25) as response:
                data=json.load(response);event['http']=response.status
        except urllib.error.HTTPError as exc:
            event['http']=exc.code
            if provider=='musicbrainz' and exc.code==404:data={'recordings':[],'_not_found':True}
            else:self.stop.set();raise RuntimeError(f'{provider} HTTP {exc.code}; stopped') from None
        except (TimeoutError,urllib.error.URLError,json.JSONDecodeError) as exc:
            self.stop.set();event['error']=type(exc).__name__;raise RuntimeError(provider+' '+type(exc).__name__) from None
        if 'error' in data:
            # Deezer code 800 is an absent recording, not a provider/access failure.
            absent=(provider=='deezer' and isinstance(data['error'],dict) and data['error'].get('code')==800)
            absent|=(provider=='lastfm' and data['error']==6)
            if not absent:self.stop.set();raise RuntimeError(f'{provider} API error (see sanitized result)')
        event['seconds']=time.time()-started
        save(cache,{'source':provider,'request':json.loads(identity),'fetched_at':time.time(),'expires_at':time.time()+TTL,'response':data})
        return data
    def lf_tags(self,kind,artist,title):
        data=self.get('lastfm','',{'method':kind+'.getTopTags','artist':artist,kind:title,'autocorrect':0})
        tags=data.get('toptags',{}).get('tag',[])
        if isinstance(tags,dict):tags=[tags]
        return [t['name'] for t in tags if int(t.get('count',0))>0]

def album_matches(movie,title):
    low=title.casefold()
    if not any(x in low for x in ('original motion picture','original soundtrack','original score')):return False
    if any(x in low for x in ('karaoke','tribute','reimagined','inspired by','cover version')):return False
    stem=re.split(r'[\[(]',title)[0]
    return norm(stem) in {norm(movie['title']),norm(movie['originalTitle'])}

def catalogue(case):
    films=json.loads((ROOT/'android/assets/movies.json').read_text(encoding='utf-8'))['movies']
    films=[m for m in films if m['rating']>=7 and m['votes']>=10000 and 'IN' not in m.get('countries',[])]
    if case=='eighties-sf':films=[m for m in films if 1980<=m['year']<1990 and 'Sci-Fi' in m['genres']]
    return sorted(films,key=lambda m:(-m['rating'],-m['votes'],m['id']))

def album_tracks(client,album):
    tracks=[];index=0
    while True:
        page=client.get('deezer',f'album/{album}/tracks',{'limit':100,'index':index});tracks.extend(page.get('data',[]))
        if not page.get('next'):return tracks
        index+=len(page['data'])

def spotify(client,isrc):
    hits=client.get('spotify','api/music/search',post={'query':'isrc:'+isrc}).get('tracks',[])
    return next((x for x in hits if x.get('is_playable') is True and x.get('external_ids',{}).get('isrc','').upper()==isrc.upper()),None)

def ost_movie(client,rules,case,movie):
    record={'movie_id':movie['id'],'movie_title':movie['title'],'countries':movie.get('countries',[]),'tracks':[]}
    hits=client.get('deezer','search/album',{'q':movie['originalTitle']+' soundtrack','limit':25})
    album=next((a for a in hits.get('data',[]) if album_matches(movie,a['title'])),None)
    if not album:return dict(record,skip='no_matching_ost_album')
    record.update(album_id=album['id'],album_title=album['title'],album_artist=album['artist']['name'])
    tags=client.lf_tags('album',album['artist']['name'],album['title']);record['album_tags']=tags
    if not tags:return dict(record,skip='album_tags_empty')
    if not rules.matches(tags,case):return dict(record,skip='album_tags_do_not_match')
    matching=[]
    for track in album_tracks(client,album['id']):
        ts=client.lf_tags('track',track['artist']['name'],track['title']);t={'deezer_track_id':track['id'],'title':track['title'],'tags':ts,'rank':track.get('rank'),'isrc':track.get('isrc')}
        record['tracks'].append(t)
        if not ts:t['skip']='track_tags_empty';continue
        if not rules.matches(ts,case):t['skip']='track_tags_do_not_match';continue
        if not isinstance(track.get('rank'),(int,float)):raise RuntimeError('Deezer rank missing; stop')
        matching.append(t)
    top=sorted(matching,key=lambda t:(-t['rank'],str(t['deezer_track_id'])))[:3]
    for t in top:
        if not t['isrc']:
            detail=client.get('deezer',f'track/{t["deezer_track_id"]}');t['isrc']=detail.get('isrc')
        if not t['isrc']:t['skip']='isrc_missing';continue
        hit=spotify(client,t['isrc']);t['spotify_uri']=hit['uri'] if hit else None;t['is_playable']=bool(hit)
        if not hit:t['skip']='no_playable_exact_spotify_isrc'
    record['top3']=top;return record

def run_ost(client,rules,case,args):
    records=[];found={};films=catalogue(case)[:args.films]
    with ThreadPoolExecutor(max_workers=4) as pool:
        for start in range(0,len(films),4):
            batch=list(pool.map(lambda m:ost_movie(client,rules,case,m),films[start:start+4]));records.extend(batch)
            for r in batch:
                for t in r.get('top3',[]):
                    if t.get('is_playable'):found[t['spotify_uri']]={'movie_id':r['movie_id'],'album_id':r['album_id'],**t}
            save(client.folder/'progress.json',{'films':records,'found':list(found.values())})
            if len(records)%40==0 or len(found)>=args.target:print(case,'ost-first films',len(records),'found',len(found),flush=True)
            if len(found)>=args.target:break
    return {'films':records,'found':list(found.values()),'album_tag_queries':sum('album_tags' in r for r in records),'album_tags_empty':sum(r.get('album_tags')==[] for r in records),'track_tag_queries':sum(len(r['tracks']) for r in records),'track_tags_empty':sum(not t['tags'] for r in records for t in r['tracks'])}

def tag_identity(t):return t.get('mbid') or norm(t['artist']['name'])+'|'+norm(t['name'])
def run_tag(client,rules,case,args):
    union={};tag_results=[]
    for group in rules.groups(case):
        for tag in group:
            data=client.get('lastfm','',{'method':'tag.getTopTracks','tag':tag,'limit':args.per_tag,'page':1})
            tracks=data.get('tracks',{}).get('track',[]);tag_results.append({'tag':tag,'returned':len(tracks)})
            for position,t in enumerate(tracks):
                identity=tag_identity(t)
                if identity not in union:union[identity]={'title':t['name'],'artist':t['artist']['name'],'mbid':t.get('mbid'),'source_tags':[],'best_position':position}
                union[identity]['source_tags'].append(tag)
                union[identity]['best_position']=min(position,union[identity]['best_position'])
    # Round-robin list positions, rather than exhausting the first tag first.
    eligible=sorted(union.values(),key=lambda x:x['best_position'])[:args.tag_candidates]
    def inspect_tags(candidate):
        candidate['tags']=client.lf_tags('track',candidate['artist'],candidate['title'])
        candidate['intent_matches']=rules.matches(candidate['source_tags']+candidate['tags'],case)
        return candidate
    with ThreadPoolExecutor(max_workers=4) as pool:
        eligible=list(pool.map(inspect_tags,eligible))
    tagged=eligible
    eligible=[x for x in tagged if x['intent_matches']]
    movies=catalogue(case);found={};records=[]
    for candidate in eligible[:args.tag_candidates]:
        record=dict(candidate);records.append(record)
        if not candidate['mbid']:record['skip']='no_recording_mbid';continue
        mb=client.get('musicbrainz','recording/'+candidate['mbid'],{'inc':'isrcs','fmt':'json'})
        isrcs=mb.get('isrcs',[]);record['isrcs']=isrcs;record['ost_checks']=[]
        if not isrcs:record['skip']='no_isrc_for_recording';continue
        # Check all returned ISRCs. No fuzzy title substitution.
        for isrc in isrcs:
            hit=client.get('deezer','track/isrc:'+isrc)
            if 'error' in hit:continue
            album=hit.get('album',{});matches=[m for m in movies if album_matches(m,album.get('title',''))]
            if len(matches)!=1:continue
            movie=matches[0];members=album_tracks(client,album['id'])
            member=next((t for t in members if str(t['id'])==str(hit['id']) or t.get('isrc')==isrc),None)
            if not member:continue
            check={'movie_id':movie['id'],'album_id':album['id'],'deezer_track_id':hit['id'],'rank':hit.get('rank'),'isrc':isrc,'membership_verified':True};record['ost_checks'].append(check)
            spot=spotify(client,isrc);check['spotify_uri']=spot['uri'] if spot else None;check['is_playable']=bool(spot)
            if spot:found[spot['uri']]={**check,'title':candidate['title'],'source_tags':candidate['source_tags']}
        if not record['ost_checks']:record['skip']='no_ost_membership_in_returned_deezer_albums'
        if len(records)%20==0:print(case,'tag-first checked',len(records),'found',len(found),flush=True)
        save(client.folder/'progress.json',{'tag_results':tag_results,'union_count':len(union),'eligible_count':len(eligible),'records':records,'found':list(found.values())})
        if len(found)>=args.target:break
    return {'tag_results':tag_results,'union_count':len(union),'intent_matched_candidates':len(eligible),'records':records,'found':list(found.values()),'tagged_candidates':tagged,'album_tag_queries':0,'track_tag_queries':len(tagged),'track_tags_empty':sum(not t['tags'] for t in tagged),'coverage_limit':'Only Deezer albums returned for exact ISRC lookups are membership-checked; other releases may exist.'}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--session-file',type=Path,required=True);parser.add_argument('--films',type=int,default=1000)
    parser.add_argument('--per-tag',type=int,default=100);parser.add_argument('--tag-candidates',type=int,default=1000);parser.add_argument('--target',type=int,default=20)
    parser.add_argument('--flow',choices=['ost-first','tag-first','both'],default='both');parser.add_argument('--case',choices=['rainy-jazz','eighties-sf','both'],default='both')
    args=parser.parse_args();key=(ROOT/'work/lastfm-api-key.txt').read_text(encoding='utf-8-sig').strip();cookie=args.session_file.read_text(encoding='utf-8-sig').strip()
    rules=Rules(ROOT/'config/music-tags.json')
    for case in (['rainy-jazz','eighties-sf'] if args.case=='both' else [args.case]):
        for flow in (['ost-first','tag-first'] if args.flow=='both' else [args.flow]):
            folder=ROOT/'work/tag-flow-comparison'/case/flow;client=Client(folder,key,cookie);started=time.time()
            print('START',case,flow,flush=True)
            result={'case':case,'flow':flow,'dictionary':rules.data,'limits':{'films':args.films,'tracks_per_tag':args.per_tag,'tag_candidates':args.tag_candidates,'target':args.target},'started_at':started}
            try:result.update((run_ost if flow=='ost-first' else run_tag)(client,rules,case,args));result['complete']=True
            except Exception as exc:result.update(complete=False,error=str(exc));raise
            finally:
                result.update(elapsed_seconds=round(time.time()-started,3),counts=dict(client.counts),events=client.events);save(folder/'result.json',result)
                print('RESULT',case,flow,'complete',result['complete'],'found',len(result.get('found',[])),'seconds',result['elapsed_seconds'],'calls',dict(client.counts),flush=True)

if __name__=='__main__':main()
