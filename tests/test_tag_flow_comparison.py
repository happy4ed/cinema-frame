"""Offline/MOCK tests; never evidence of provider coverage or actual playback."""
import io
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import compare_tag_flows as c

class TagFlowTests(unittest.TestCase):
    def setUp(self):self.rules=c.Rules(c.ROOT/'config/music-tags.json')

    def test_jazz_subgenres_and_mood_are_separate_constraints(self):
        for tag in ('bebop','swing','BigBand','hard-bop','vocal jazz'):
            self.assertTrue(self.rules.matches([tag,'mellow'],'rainy-jazz'))
        self.assertFalse(self.rules.matches(['jazz'],'rainy-jazz'))
        self.assertFalse(self.rules.matches(['relaxing'],'rainy-jazz'))
        self.assertFalse(self.rules.matches(['my rainy jazz favourites'],'rainy-jazz'))
        self.assertEqual(self.rules.data['genres']['big band'],['big band'])

    def test_mock_rank_top3_is_chosen_after_track_tag_filter(self):
        class MockClient:
            def get(self,provider,path,params=None,post=None):
                if path=='search/album':return {'data':[{'id':1,'title':'Example (Original Soundtrack)','artist':{'name':'Composer'}}]}
                if path=='album/1/tracks':return {'data':[{'id':i,'title':str(i),'artist':{'name':'Composer'},'rank':100-i,'isrc':str(i)} for i in range(1,6)]}
                if path=='api/music/search':
                    isrc=post['query'][5:]
                    return {'tracks':[{'uri':'spotify:track:'+isrc,'is_playable':True,'external_ids':{'isrc':isrc}}]}
                raise AssertionError(path)
            def lf_tags(self,kind,artist,title):
                return ['jazz','calm'] if kind=='album' or title!='1' else ['rock']
        result=c.ost_movie(MockClient(),self.rules,'rainy-jazz',{'id':'mock','title':'Example','originalTitle':'Example'})
        self.assertEqual([t['deezer_track_id'] for t in result['top3']],[2,3,4])

    def test_mock_spotify_rejects_wrong_isrc_and_missing_playability(self):
        class MockClient:
            def get(self,*args,**kwargs):return {'tracks':[
                {'uri':'wrong','is_playable':True,'external_ids':{'isrc':'OTHER'}},
                {'uri':'unknown','external_ids':{'isrc':'EXACT'}}]}
        self.assertIsNone(c.spotify(MockClient(),'EXACT'))

    def test_mock_tag_list_membership_survives_empty_track_tags(self):
        class MockClient:
            def get(self,provider,path,params=None,post=None):
                if provider=='musicbrainz':return {'isrcs':[]}
                tracks=([{'mbid':'mock-id','name':'Mock','artist':{'name':'Mock'}}]
                        if params['tag'] in ('jazz','calm') else [])
                return {'tracks':{'track':tracks}}
            def lf_tags(self,*args):return []
        client=MockClient()
        with tempfile.TemporaryDirectory() as tmp, patch.object(c,'catalogue',return_value=[]):
            client.folder=Path(tmp)
            result=c.run_tag(client,self.rules,'rainy-jazz',SimpleNamespace(per_tag=100,tag_candidates=1000,target=20))
        self.assertEqual(result['intent_matched_candidates'],1)
        self.assertEqual(result['track_tags_empty'],1)
        self.assertEqual(result['found'],[])

    def test_mock_cache_expires_at_seven_days_and_does_not_store_key(self):
        with tempfile.TemporaryDirectory() as tmp:
            client=c.Client(Path(tmp),'SECRET_TEST_VALUE','COOKIE_TEST_VALUE')
            def response(*args,**kwargs):
                result=io.BytesIO(b'{"toptags":{"tag":[]}}');result.status=200;return result
            with patch.object(c.urllib.request,'urlopen',side_effect=response) as network, patch.object(c.time,'time',return_value=100):
                client.get('lastfm','',{'method':'track.getTopTags'})
                client.get('lastfm','',{'method':'track.getTopTags'})
                self.assertEqual(network.call_count,1)
            raw=next((Path(tmp)/'cache').glob('*.json')).read_text()
            self.assertNotIn('SECRET_TEST_VALUE',raw)
            self.assertNotIn('COOKIE_TEST_VALUE',raw)
            with patch.object(c.urllib.request,'urlopen',side_effect=response) as network, patch.object(c.time,'time',return_value=100+c.TTL):
                client.get('lastfm','',{'method':'track.getTopTags'})
                self.assertEqual(network.call_count,1)

if __name__=='__main__':unittest.main()
