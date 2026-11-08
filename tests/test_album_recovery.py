"""MOCK metadata regression fixtures; not a substitute for provider audit."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from recover_ost_albums import title_match, release_check

class AlbumIdentityRegression(unittest.TestCase):
    def test_movie_title_containing_score_is_preserved(self):
        self.assertTrue(title_match(['The Score'], 'The Score (Original Motion Picture Soundtrack)'))
        self.assertFalse(title_match(['The Score'], 'The (Original Motion Picture Soundtrack)'))

    def test_sequel_is_not_parent_film(self):
        self.assertFalse(title_match(['The Hunger Games'], 'The Hunger Games: The Ballad of Songbirds & Snakes (Original Motion Picture Soundtrack)'))

    def test_suite_and_arrangement_rejected(self):
        self.assertFalse(title_match(['Back to the Future'], 'Back to the Future Suite'))
        self.assertFalse(title_match(['Gladiator'], 'Gladiator (Arr. for piano)'))

    def test_single_rejected_even_with_matching_title(self):
        self.assertEqual(release_check({'year': 2008}, {'album_type': 'single'}, {'title': 'Gran Torino'}), 'single_not_ost_album')

    def test_bare_title_needs_original_release_evidence(self):
        movie={'year': 2008}
        album={'title': 'Gran Torino', 'record_type': 'album'}
        self.assertIsNotNone(release_check(movie, {'release_date': '2025-01-01'}, album))
        self.assertIsNone(release_check(movie, {'release_date': '2025-01-01', 'copyrights': [{'text': '(P) 2008'}]}, album))

if __name__ == '__main__': unittest.main(verbosity=2)
