"""MOCK network/clock tests. Real mapping statistics are recorded separately."""
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'tools'))
import wikidata_countries as wc

class Clock:
    now = 1000.0
    def time(self): return self.now
    def sleep(self, seconds): self.now += seconds

def response(ids):
    return io.BytesIO(json.dumps({'results': {'bindings': [{'imdb': {'value': i}} for i in ids]}}).encode())

class CountryCollectorTest(unittest.TestCase):
    def test_mock_retry_after_pagination_resume_and_country_separation(self):
        clock=Clock(); starts=[]
        responses=[urllib.error.HTTPError('https://query.wikidata.org',429,'limited',{'Retry-After':'120'},None),
                   ['tt1','nm9'],['tt2'],['tt2']]
        def fetch(req, timeout):
            starts.append(clock.time())
            self.assertIn('CinemaFrame', req.headers['User-agent'])
            self.assertIn('https://github.com/happy4ed/cinema-frame/issues', req.headers['User-agent'])
            value=responses.pop(0)
            if isinstance(value,Exception): raise value
            return response(value)
        with tempfile.TemporaryDirectory() as tmp, patch.object(wc,'COUNTRIES',{'CN':'Q148','HK':'Q8646'}), patch.object(wc.time,'time',clock.time), patch.object(wc.time,'sleep',clock.sleep), patch.object(wc.urllib.request,'urlopen',fetch):
            cp=Path(tmp)/'state.json';state=wc.collect(cp,page_size=2)
            self.assertEqual(starts,[1000,1120,1181,1242])
            self.assertEqual(state['countries']['CN']['offset'],3) # Includes rejected row for pagination.
            self.assertEqual(state['countries']['CN']['pages'][1]['offset'],2)
            wc.collect(cp,page_size=2)
            self.assertEqual(len(starts),4) # Complete pages were not fetched again.
            movies=[{'id':'tt1'},{'id':'tt2'},{'id':'tt3'}];stats=wc.enrich(movies,state)
            self.assertEqual(movies,[{'id':'tt1','countries':['CN']},{'id':'tt2','countries':['CN','HK']},{'id':'tt3','countries':[]}])
            self.assertEqual((stats['total'],stats['mapped'],stats['unknown']),(3,2,1))
            self.assertEqual(stats['by_country'],{'CN':2,'HK':1})

    def test_mock_incomplete_checkpoint_cannot_replace_catalogue(self):
        movies=[{'id':'tt1','countries':['US']}]
        with self.assertRaises(ValueError): wc.enrich(movies,{'countries':{}})
        self.assertEqual(movies[0]['countries'],['US'])

    def test_mock_retry_after_http_date(self):
        self.assertEqual(wc.retry_delay('Thu, 01 Jan 1970 00:20:00 GMT',1000),200)
        self.assertEqual(wc.retry_delay(None,1000),61)

if __name__=='__main__': unittest.main(verbosity=2)
