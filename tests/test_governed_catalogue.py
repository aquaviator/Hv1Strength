import importlib.util
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).parents[1]
SPEC = importlib.util.spec_from_file_location("governed_catalogue", ROOT / "tools/governed_catalogue.py")
MODULE = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(MODULE)


class GovernedCatalogueTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = json.loads((ROOT / "app/src/main/assets/strength-exercise-catalogue.json").read_text(encoding="utf-8"))
        cls.release, cls.items = MODULE.build_release(cls.source, "test-release", "2026-08-14T00:00:00Z")

    def test_all_original_ids_are_preserved(self):
        self.assertEqual({x["id"] for x in self.source["exercises"]}, {x["exerciseId"] for x in self.items})
        self.assertEqual(264, len(self.items))

    def test_release_is_deterministic(self):
        release2, items2 = MODULE.build_release(self.source, "test-release", "2026-08-14T00:00:00Z")
        self.assertEqual(self.release, release2); self.assertEqual(self.items, items2)
        self.assertEqual(self.release["contentSha256"], MODULE.sha256(self.items))

    def test_existing_alias_collision_is_audited_and_resolved(self):
        item = next(value for value in self.items if value["exerciseId"] == "reverse_pec_deck")
        self.assertIn("Reverse Pec Deck", item["canonicalName"])
        self.assertIn("machine rear delt fly", item["suppressedAliases"])

    def test_reference_and_capability_failures_are_rejected(self):
        bad = [dict(item) for item in self.items]
        bad[0] = dict(bad[0], relatedExerciseIds=[bad[0]["exerciseId"]], trackingCapabilities=["assisted_load"])
        errors = MODULE.validate_exercises(bad)
        self.assertTrue(any("self reference" in error for error in errors))
        self.assertTrue(any("requires bodyweight" in error for error in errors))

    def test_alias_collision_is_rejected(self):
        bad = [dict(item) for item in self.items]
        bad[1] = dict(bad[1], aliases=[bad[0]["canonicalName"]])
        self.assertTrue(any("collision" in error for error in MODULE.validate_exercises(bad)))

    def test_staging_classification_is_fail_closed(self):
        ids = {item["exerciseId"] for item in self.items}; terms = {term for item in self.items for term in item["normalizedNames"]}
        self.assertEqual("Already represented by a governed ID", MODULE.classify_staging({"id": self.items[0]["exerciseId"]}, ids, terms))
        self.assertEqual("Missing required data", MODULE.classify_staging({"id": "candidate"}, ids, terms))
        self.assertEqual("Requires safety review", MODULE.classify_staging({"id": "new_item", "name": "New Item", "category": "Legs", "capabilities": ["repetitions"]}, ids, terms))

    def test_production_project_cannot_be_written(self):
        with self.assertRaisesRegex(ValueError, "demo-"):
            MODULE.emulator_write("hv1-platform", self.release, self.items)


if __name__ == "__main__": unittest.main()
