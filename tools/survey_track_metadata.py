"""Read-only real-provider survey. Does not alter frame selection or playback."""
import argparse
import datetime as dt
import json
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request

UA='CinemaFrame/0.6 (https://github.com/happy4ed/cinema-frame/issues; track metadata survey)'
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'work/track-metadata-survey'
LASTFM_KEY=ROOT/'work/lastfm-api-key.txt'
last_mb=0.0
last_lf=0.0

def save(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_suffix('.tmp');temp.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8');temp.replace(path)

def get(url, dest):
    global last_mb,last_lf
    if dest.exists(): return json.loads(dest.read_text(encoding='utf-8'))
    if 'musicbrainz.org' in url:
        time.sleep(max(0,1.1-(time.monotonic()-last_mb)));last_mb=time.monotonic()
    if 'audioscrobbler.com' in url:
        time.sleep(max(0,1.1-(time.monotonic()-last_lf)));last_lf=time.monotonic()
    try:
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':UA}),timeout=25) as response:
            obj=json.load(response)
    except urllib.error.HTTPError as exc:
        # Never persist request URLs containing API keys.
        obj={'_http_error':exc.code,'_retry_after':exc.headers.get('Retry-After')}
    except (TimeoutError,urllib.error.URLError) as exc:
        obj={'_transport_error':type(exc).__name__}
    save(dest,obj)
    if obj.get('_http_error')==429:raise RuntimeError('Provider rate limited; see checkpoint, stop survey')
    return obj

def norm(s):return ''.join(c.lower() for c in s if c.isalnum())
def album_matches(movie,title):
    low=title.lower()
    return any(x in low for x in ('original motion picture','original soundtrack','original score')) and not any(x in low for x in ('karaoke','tribute','reimagined','inspired by','cover version')) and norm(re.split(r'[\[(]',title)[0]) in (norm(movie['title']),norm(movie['originalTitle']))

def candidates(name):
    target=OUT/(name+'-candidates.json')
    if target.exists():return json.loads(target.read_text(encoding='utf-8'))
    movies=json.loads((ROOT/'android/assets/movies.json').read_text(encoding='utf-8'))['movies']
    india=set(json.loads((ROOT/'android/assets/film-countries.json').read_text(encoding='utf-8'))['india'])
    movies=[m for m in movies if m['rating']>=7 and m['votes']>=10000 and m['id'] not in india]
    if name=='eighties-sf':movies=[m for m in movies if 1980<=m['year']<1990 and 'Sci-Fi' in m['genres']]
    movies.sort(key=lambda m:(-m['rating'],-m['votes'],m['id']))
    result={'request':'비 오는 날 재즈' if name=='rainy-jazz' else '80년대 SF 웅장한','sampling':'rating desc, votes desc; no film Music restriction; exact OST album, Deezer top3; first 20 distinct ISRCs BEFORE tag/mood filtering','film_scan_cap':100,'films_checked':[],'tracks':[]}
    seen=set()
    for movie in movies[:100]:
        hits=get('https://api.deezer.com/search/album?'+urllib.parse.urlencode({'q':movie['originalTitle']+' soundtrack','limit':25}),OUT/'raw'/('search-'+movie['id']+'.json'))
        if '_http_error' in hits or 'error' in hits:raise RuntimeError('Deezer album API error')
        selected=next((a for a in hits.get('data',[]) if album_matches(movie,a['title'])),None)
        entry={'movie_id':movie['id'],'title':movie['title'],'album_id':selected['id'] if selected else None};result['films_checked'].append(entry)
        if selected:
            tracks=[];url=f'https://api.deezer.com/album/{selected["id"]}/tracks?limit=100';page=0
            while url:
                data=get(url,OUT/'raw'/f'album-{selected["id"]}-{page}.json');tracks.extend(data.get('data',[]));url=data.get('next');page+=1
                if '_http_error' in data or 'error' in data:raise RuntimeError('Deezer album tracks API error')
            if any('rank' not in t for t in tracks):raise RuntimeError('Missing Deezer rank; stop')
            for t in sorted(tracks,key=lambda t:(-t['rank'],str(t['id'])))[:3]:
                d=get('https://api.deezer.com/track/'+str(t['id']),OUT/'raw'/f'track-{t["id"]}.json')
                isrc=d.get('isrc')
                if not isrc or isrc in seen:continue
                seen.add(isrc);result['tracks'].append({'movie_id':movie['id'],'movie_title':movie['title'],'deezer_album_id':selected['id'],'deezer_track_id':t['id'],'title':d['title'],'artist':d['artist']['name'],'rank':d['rank'],'isrc':isrc})
                if len(result['tracks'])==20:break
        save(OUT/(name+'-sampling-progress.json'),result)
        print(name,'films',len(result['films_checked']),'tracks',len(result['tracks']),flush=True)
        if len(result['tracks'])>=20:break
    save(target,result);return result

def survey(name):
    sample=candidates(name);records=[]
    for t in sample['tracks']:
        d=get('https://api.deezer.com/track/'+str(t['deezer_track_id']),OUT/'raw'/f'track-{t["deezer_track_id"]}.json')
        mb=get('https://musicbrainz.org/ws/2/isrc/'+t['isrc']+'?inc=artists+tags&fmt=json',OUT/'raw'/('mb-'+t['isrc']+'.json'))
        tags=[tag['name'] for r in mb.get('recordings',[]) for tag in r.get('tags',[]) if tag.get('count',0)>0]
        lf=None;lf_lookup='artist_track';lf_direct_tags=[]
        if LASTFM_KEY.exists():
            key=LASTFM_KEY.read_text(encoding='utf-8-sig').strip()
            if key:
                if not re.fullmatch('[a-fA-F0-9]{32}',key):raise RuntimeError('Last.fm key file must contain only the 32-character API key')
                url='https://ws.audioscrobbler.com/2.0/?'+urllib.parse.urlencode({'method':'track.getTopTags','api_key':key,'artist':t['artist'],'track':t['title'],'autocorrect':0,'format':'json'})
                lf=get(url,OUT/'raw'/('lf-'+str(t['deezer_track_id'])+'.json'))
                if lf.get('error') in (4,9,10,26,29):raise RuntimeError('Last.fm access/rate error code '+str(lf['error']))
                lf_direct_tags=[x['name'] for x in lf.get('toptags',{}).get('tag',[]) if int(x.get('count',0))>0]
                if not lf_direct_tags and len(mb.get('recordings',[]))==1:
                    mbid=mb['recordings'][0]['id']
                    url='https://ws.audioscrobbler.com/2.0/?'+urllib.parse.urlencode({'method':'track.getTopTags','api_key':key,'mbid':mbid,'autocorrect':0,'format':'json'})
                    lf=get(url,OUT/'raw'/('lf-mbid-'+mbid+'.json'));lf_lookup='exact_isrc_unique_mbid'
                    if lf.get('error') in (4,9,10,26,29):raise RuntimeError('Last.fm access/rate error code '+str(lf['error']))
        ltags=[x['name'] for x in (lf or {}).get('toptags',{}).get('tag',[]) if int(x.get('count',0))>0]
        bpm=d.get('bpm');valid=isinstance(bpm,(int,float)) and bpm>0
        source_tags=ltags if ltags else tags
        normalized={x.lower().strip() for x in source_tags}
        record={**t,'bpm_field_present':'bpm' in d,'bpm_raw':bpm,'bpm_valid':valid,'mb_recordings':len(mb.get('recordings',[])),'mb_tags':sorted(set(tags)),'mb_http_error':mb.get('_http_error'),'lastfm_checked':lf is not None,'lastfm_tags':ltags,'lastfm_error':(lf or {}).get('error',(lf or {}).get('_http_error')),'selected_tag_source':'Last.fm' if ltags else 'MusicBrainz' if tags else None,'jazz_tag_exact':'jazz' in normalized,'epic_tag':bool(normalized&{'epic','orchestral','cinematic'}),'calm_rule':(valid and bpm<=95) or bool(normalized&{'calm','chill','melancholy'}),'upbeat_rule':(valid and bpm>=110) or 'upbeat' in normalized}
        record.update(lastfm_lookup=lf_lookup,lastfm_direct_tags=lf_direct_tags)
        records.append(record);save(OUT/(name+'-survey.json'),{'sample':sample,'records':records})
        print(name,len(records),'bpm',bpm,'mb_tags',len(tags),'lastfm',len(ltags) if lf is not None else 'not configured',flush=True)
    valid_bpms=sorted(t['bpm_raw'] for t in records if t['bpm_valid'])
    summary={'request':sample['request'],'sample_size':len(records),'films_scanned':len(sample['films_checked']),'film_scan_cap':100,'bpm_field':sum(t['bpm_field_present'] for t in records),'bpm_valid':len(valid_bpms),'bpm_zero':sum(t['bpm_raw']==0 for t in records),'bpm_distribution':valid_bpms,'musicbrainz_recording_found':sum(t['mb_recordings']>0 for t in records),'musicbrainz_tags_nonempty':sum(bool(t['mb_tags']) for t in records),'lastfm_checked':sum(t['lastfm_checked'] for t in records),'lastfm_tags_nonempty':sum(bool(t['lastfm_tags']) for t in records),'jazz_tag_exact':sum(t['jazz_tag_exact'] for t in records),'calm_rule':sum(t['calm_rule'] for t in records),'epic_rule':sum(t['epic_tag'] for t in records),'upbeat_rule':sum(t['upbeat_rule'] for t in records),'request_pass':sum(t['jazz_tag_exact'] and t['calm_rule'] if name=='rainy-jazz' else t['epic_tag'] for t in records),'checked_at':dt.datetime.now(dt.timezone.utc).isoformat()}
    summary['lastfm_direct_tags_nonempty']=sum(bool(t['lastfm_direct_tags']) for t in records)
    summary['lastfm_mbid_checked']=sum(t['lastfm_lookup']=='exact_isrc_unique_mbid' for t in records)
    save(OUT/(name+'-summary.json'),summary);print(json.dumps(summary,ensure_ascii=False),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--sample',choices=['rainy-jazz','eighties-sf','both'],default='both');args=parser.parse_args()
    for name in (['rainy-jazz','eighties-sf'] if args.sample=='both' else [args.sample]):survey(name)
