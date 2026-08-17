import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).parents[1] / "tools"))
from editorial_catalogue import build_release_draft, classify, transition, validate_evidence, validate_rich_exercise


class EditorialCatalogueTest(unittest.TestCase):
    def setUp(self):
        self.base = {"catalogueVersion": "draft", "exercises": [{"id": "bench_press", "name": "Bench Press", "aliases": ["Barbell Bench"], "category": "Chest", "equipment": ["barbell"]}]}

    def test_exact_name_and_alias_are_explainable(self):
        exact = classify({"candidateId": "a", "name": "Bench-Press"}, self.base["exercises"])
        alias = classify({"candidateId": "b", "name": "barbell bench"}, self.base["exercises"])
        self.assertEqual((exact["recommendation"], alias["recommendation"]), ("ALIAS", "ALIAS"))
        self.assertIn("exact", " ".join(exact["reasons"]))

    def test_near_name_is_not_silently_merged(self):
        result = classify({"candidateId": "a", "name": "Bench Step Up", "category": "Legs", "equipment": ["bench"]}, self.base["exercises"])
        self.assertNotEqual(result["recommendation"], "ALIAS")

    def test_lifecycle_rejects_skips(self):
        with self.assertRaises(ValueError): transition({"lifecycle": "INGESTED"}, "APPROVED")
        self.assertEqual(transition({"lifecycle": "INGESTED"}, "CLASSIFIED")["lifecycle"], "CLASSIFIED")

    def test_evidence_requires_attribution(self):
        self.assertTrue(validate_evidence([{"claim": "x"}]))
        self.assertFalse(validate_evidence([{"claim": "x", "citation": "Study", "sourceUrl": "https://example.test", "reviewedAt": "2026-08-17", "reviewer": "editor"}]))

    def test_rich_schema_is_additive(self):
        item = {"id": "split_squat", "name": "Split Squat", "category": "Legs", "jointActions": ["knee extension"], "evidence": []}
        self.assertEqual(validate_rich_exercise(item), [])

    def test_only_approved_decisions_enter_draft_and_ids_stay_stable(self):
        candidates = [{"candidateId": "a", "exercise": {"id": "split_squat", "name": "Split Squat", "category": "Legs"}}, {"candidateId": "b", "exercise": {"name": "Chest Press"}}]
        decisions = [{"candidateId": "a", "lifecycle": "APPROVED", "decision": "NEW_EXERCISE"}, {"candidateId": "b", "lifecycle": "REVIEWED", "decision": "ALIAS", "canonicalExerciseId": "bench_press"}]
        first = build_release_draft(self.base, candidates, decisions); second = build_release_draft(self.base, candidates, decisions)
        self.assertEqual(first, second); self.assertEqual([e["id"] for e in first["exercises"]], ["bench_press", "split_squat"])
        self.assertEqual(first["exercises"][0]["aliases"], ["Barbell Bench"])


if __name__ == "__main__": unittest.main()
