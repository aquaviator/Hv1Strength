import sys, unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
from catalogue_readiness import DUPLICATES, duplicate_audit, singular_tokens

class CatalogueReadinessTests(unittest.TestCase):
    def test_word_order_and_plural_normalization(self):
        self.assertEqual(sorted(singular_tokens("Face Pulls")), sorted(singular_tokens("Face Pull")))
        self.assertEqual(sorted(singular_tokens("Machine Hip Abduction")), sorted(singular_tokens("Hip Abduction Machine")))
    def test_directional_variations_remain_distinct(self):
        items=[{"exerciseId":"a","canonicalName":"Cable High-to-Low Flye","equipment":["cable"]},
               {"exerciseId":"b","canonicalName":"Cable Low-to-High Flye","equipment":["cable"]}]
        audit=duplicate_audit(items)
        self.assertEqual(audit["exactDuplicateGroups"], [])
        self.assertEqual(audit["wordOrderOrSingularGroups"], [])
        self.assertEqual(audit["probableDuplicateGroups"], [])
    def test_duplicate_overrides_are_not_self_referential(self):
        self.assertTrue(DUPLICATES)
        self.assertTrue(all(left!=right for left,right in DUPLICATES.items()))

if __name__=="__main__": unittest.main()
