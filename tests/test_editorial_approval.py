import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))

from editorial_approval import capabilities, duplicate_pairs, projection, specialist_reasons


class EditorialApprovalTests(unittest.TestCase):
    def exercise(self, name="Dumbbell Press", category="strength", equipment=None):
        return {"displayName": name, "proposedCanonicalStableId": "dumbbell_press", "category": category,
                "equipment": equipment or ["Dumbbell"], "movementPatterns": ["Horizontal Push"],
                "primaryMuscles": ["Chest"], "secondaryMuscles": ["Triceps"], "aliases": [], "laterality": "bilateral"}

    def test_core_projection_excludes_advanced_intelligence(self):
        item = projection(self.exercise())
        self.assertEqual("approved", item["editorialStatus"])
        self.assertEqual("evidence_not_supplied_core_only", item["evidenceSummaryState"])
        self.assertNotIn("coachingIntelligence", item)
        self.assertIn("load", item["trackingCapabilities"])

    def test_mobility_does_not_gain_strength_metrics(self):
        caps = capabilities(self.exercise("Hip Stretch", "mobility", ["Bodyweight"]), "Mobility")
        self.assertEqual(["duration", "rpe"], caps)

    def test_rehabilitation_and_olympic_records_defer(self):
        self.assertTrue(specialist_reasons(self.exercise(category="rehabilitation")))
        self.assertTrue(specialist_reasons(self.exercise("Barbell Power Snatch")))

    def test_meaningful_equipment_variations_are_not_duplicates(self):
        left = {"documentId": "a", "fields": {"exercise": self.exercise("Bench Press", equipment=["Barbell"])}}
        right = {"documentId": "b", "fields": {"exercise": self.exercise("Bench Press", equipment=["Dumbbell"])}}
        self.assertEqual({}, duplicate_pairs([left, right]))


if __name__ == "__main__":
    unittest.main()
