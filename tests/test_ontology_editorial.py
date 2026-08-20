import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))

from ontology_editorial import DESTINATION_PROJECT, dry_run_validate, normalize_record, normalized_text


class OntologyEditorialTests(unittest.TestCase):
    def test_normalization_is_accent_and_punctuation_stable(self):
        self.assertEqual("barbell bench press", normalized_text("  Bárbell—Bench Press! "))

    def test_partial_percentages_are_retained_not_rejected(self):
        document = {
            "documentId": "source-1",
            "fields": {
                "title": "Test Press",
                "anatomy": {"primary_muscles": [{"name": "Chest", "involvement_percentage": 80}]},
            },
        }
        record, errors, specialist = normalize_record(document, set())
        self.assertEqual([], errors)
        self.assertEqual([], specialist)
        self.assertIn("totals 80", record["numericIntelligenceLimitation"])

    def test_dry_run_is_staging_only_and_replay_safe(self):
        record = {
            "documentId": "ontology_abc",
            "idempotencyKey": f"{DESTINATION_PROJECT}/staging_exercises/ontology_abc:checksum",
            "contentChecksum": "checksum",
        }
        receipt = dry_run_validate([record])
        self.assertTrue(receipt["identicalReplayIdempotent"])
        self.assertFalse(receipt["activeReleaseWritesPossible"])
        self.assertFalse(receipt["currentPointerWritesPossible"])
        self.assertFalse(receipt["userCollectionWritesPossible"])

    def test_dry_run_rejects_non_staging_target(self):
        record = {"documentId": "bad", "idempotencyKey": "hv1-platform/users/bad:x", "contentChecksum": "x"}
        with self.assertRaises(ValueError):
            dry_run_validate([record])


if __name__ == "__main__":
    unittest.main()
