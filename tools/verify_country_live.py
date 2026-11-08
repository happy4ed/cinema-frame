"""Authenticated real-frame country exclusion checks; no mock responses."""
import argparse
import datetime as dt
import json
from pathlib import Path
import urllib.request

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api',default='http://127.0.0.1:28787')
    parser.add_argument('--session-file',type=Path,required=True)
    parser.add_argument('--output',type=Path,default=Path('work/country-live-verified.json'))
    args=parser.parse_args();cookie=args.session_file.read_text(encoding='utf-8-sig').strip()
    def call(path,body=None):
        request=urllib.request.Request(args.api+path,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None,headers={'Cookie':'cf_session='+cookie,'Content-Type':'application/json'})
        with urllib.request.urlopen(request,timeout=25) as response:return json.load(response)
    original=call('/api/state');profile=original['profile'];mapping=original['country_mapping']
    if not mapping.get('complete'):raise RuntimeError('Frame country mapping incomplete; refusing to claim verification')
    catalogue=json.loads(Path('android/assets/movies.json').read_text(encoding='utf-8'))['movies']
    candidates=[m for m in catalogue if 1980<=m['year']<1990 and 'Sci-Fi' in m['genres'] and m['rating']>=7 and m['votes']>=10000 and m['id'] not in profile.get('excluded_movies',[])]
    evidence={'checked_at':dt.datetime.now(dt.timezone.utc).isoformat(),'profile_request':'80년대 SF 웅장한','mapping':mapping,'cases':[]}
    try:
        call('/api/request',{'text':'80년대 SF 웅장한'})
        for code in (None,'US','CN','HK'):
            call('/api/countries',{'excluded_countries':[] if code is None else [code],'enabled':True})
            actual=call('/api/selection')['candidates']
            expected=[m for m in candidates if code not in m.get('countries',[])]
            case={'excluded_country':code,'actual_count':len(actual),'expected_count':len(expected),'movies':[{k:m[k] for k in ('id','title','countries')} for m in actual]}
            evidence['cases'].append(case)
            assert {m['id'] for m in actual}=={m['id'] for m in expected},f'{code}: API/catalogue candidates differ'
            print(f'{code or "NONE"}: {len(actual)} candidates',flush=True)
        assert evidence['cases'][1]['actual_count']<evidence['cases'][0]['actual_count'],'US exclusion did not reduce candidates'
        evidence['passed']=True
    finally:
        call('/api/countries',{'excluded_countries':profile.get('excluded_countries',[]),'enabled':profile.get('country_exclusions_enabled',True)})
        if original.get('request') and original['request']!='80년대 SF 웅장한':call('/api/request',{'text':original['request']})
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
    print('PASS live country candidates: NONE / US / CN / HK; previous settings restored')

if __name__=='__main__':main()
