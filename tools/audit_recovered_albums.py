"""Real metadata audit of a fixed 30-album sample; human review still required."""
import hashlib,json,time
from compare_tag_flows import ROOT,Client,save
from recover_ost_albums import title_match

def main():
    state=json.loads((ROOT/'work/album-recovery-strict/checkpoint.json').read_text(encoding='utf-8'))
    if not state.get('complete'):raise RuntimeError('Recovery must finish before selecting audit sample')
    linked=[r for r in state['records'] if r.get('album')]
    sample=[]
    for baseline in (True,False):
        group=[r for r in linked if (r.get('method')=='baseline')==baseline]
        sample.extend(sorted(group,key=lambda r:hashlib.sha256(('album-audit-v1:'+r['movie']['id']).encode()).hexdigest())[:15])
    out=ROOT/'work/album-audit-strict-30';client=Client(out,'','');rows=[];started=time.time()
    for r in sample:
        m=r['movie'];a=client.get('deezer','album/'+str(r['album']['id']))
        rows.append({'movie_id':m['id'],'movie':m['title'],'original_title':m['originalTitle'],'movie_year':m['year'],
          'composer':r.get('credited_composers',[]),'album_id':a['id'],'album':a['title'],'artist':a.get('artist',{}).get('name'),
          'album_release':a.get('release_date'),'record_type':a.get('record_type'),'track_count':a.get('nb_tracks'),'label':a.get('label'),
          'upc':a.get('upc'),'spotify':r.get('spotify_album'),'method':r.get('method'),
          'title_check':title_match([m['title'],m['originalTitle']],a['title']),
          'upc_check':not r.get('spotify_album') or str(a.get('upc','')).lstrip('0')==str(r['spotify_album']['upc']).lstrip('0')})
    save(out/'result.json',{'sample':'15 baseline + 15 recovered; SHA256(album-audit-v1:+IMDb ID), same rule as failed audit',
        'records':rows,'counts':dict(client.counts),'elapsed_seconds':time.time()-started,'automated_failures':sum(not r['title_check'] or not r['upc_check'] or r['record_type']=='single' for r in rows),
        'manual_review':'pending'})
    print('REAL audit:',len(rows),'albums;',dict(client.counts))
    for r in rows:print(r['movie'],'=>',r['album'],'|',r['artist'],'|',r['label'],'|',r['album_release'],'|',r['record_type'],'|',r['track_count'],'| title=',r['title_check'])

if __name__=='__main__':main()
