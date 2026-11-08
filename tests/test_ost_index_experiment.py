"""MOCK/pure checks, not evidence of live provider coverage."""
from pathlib import Path
import sys
import unittest
import io
import json
import tempfile
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import experiment_ost_index as e

class IndexExperimentTests(unittest.TestCase):
    def test_artist_tags_without_count_are_not_lost(self):
        self.assertEqual(e.tags_from({'toptags':{'tag':[{'name':'jazz'}]}}),[{'name':'jazz'}])

    def test_dictionary_jazz_subgenres_and_personal_tags(self):
        rules=e.Rules(e.ROOT/'config/music-tags.json')
        for name in ('bebop','swing','big band','hard bop','latin jazz'):
            self.assertIn('jazz',e.classify_tags([{'name':name}],rules)['styles'])
        self.assertNotIn('jazz',e.classify_tags([{'name':'my jazz favourites'}],rules)['styles'])

    def test_unknown_and_missing_gemini_answers_cannot_pass(self):
        rows=[{'key':'a'}]
        valid={'key':'a','knowledge':'unknown','styles':[],'moods':[],'reason':'모름'}
        self.assertEqual(e.validate_judgments({'decisions':[valid]},rows),[valid])
        for ds in ([],[valid,valid],[{**valid,'styles':['jazz']}],[{**valid,'reason':''}]):
            with self.assertRaises(ValueError):e.validate_judgments({'decisions':ds},rows)

    def test_mock_same_title_wrong_composer_and_live_rejected(self):
        class Tmdb:
            def get(self,path):
                return {'movie_results':[{'id':1}]} if path.startswith('find/') else {'crew':[{'job':'Original Music Composer','name':'Correct Composer'}]}
        class Client:
            def get(self,provider,path,params=None):
                if path=='search/album':return {'data':[
                    {'id':1,'title':'Example (Original Soundtrack)','artist':{'name':'Other Composer'}},
                    {'id':2,'title':'Example (Original Soundtrack) [Live]','artist':{'name':'Correct Composer'}}]}
                raise AssertionError('Wrong album must never be indexed: '+path)
        result=e.collect_movie(Client(),Tmdb(),{'id':'mock','title':'Example','originalTitle':'Example'})
        self.assertEqual(result['tracks'],[])
        self.assertIn('skip',result)

    def test_mock_gemini_schema_limits_keys_to_input_identifiers(self):
        def fetch(request,timeout):
            payload=json.loads(request.data)
            key_schema=payload['generationConfig']['responseSchema']['properties']['decisions']['items']['properties']['key']
            self.assertEqual(key_schema['enum'],['a'])
            value={'decisions':[{'key':'a','knowledge':'unknown','styles':[],'moods':[],'reason':'모름'}]}
            result=io.BytesIO(json.dumps({'candidates':[{'content':{'parts':[{'text':json.dumps(value)}]}}]}).encode())
            result.status=200;return result
        with tempfile.TemporaryDirectory() as tmp, patch.object(e,'FOLDER',Path(tmp)), patch.object(e.urllib.request,'urlopen',fetch):
            result=e.gemini([{'key':'a','track':'MOCK','artist':'MOCK','films':[]}],'mock','test-no-secret',{'gemini_events':[],'gemini_cache_hits':0})
        self.assertEqual(result[0]['knowledge'],'unknown')

if __name__=='__main__':unittest.main()
