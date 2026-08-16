import importlib.util
import json
import pathlib
import unittest
from unittest import mock
import urllib.error

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

    def test_schema_is_app_neutral_for_a_second_reader(self):
        item = self.items[0]
        self.assertIn("trackingCapabilities", item)
        self.assertIn("recommendedForHiit", item)
        self.assertIn("recommendedForStrength", item)
        self.assertNotIn("humanUserId", item)
        self.assertNotIn("strengthOnly", item)

    def test_second_reader_can_consume_every_exercise_without_strength_ownership_fields(self):
        required = {"exerciseId", "schemaVersion", "displayName", "trackingCapabilities",
                    "recommendedForHiit", "recommendedForStrength", "cardioSuitable", "timedIntervalSuitable"}
        for item in self.items:
            self.assertTrue(required.issubset(item))
            self.assertNotIn("humanUserId", item)
            self.assertNotIn("firebaseUid", item)
            self.assertIsInstance(item["recommendedForHiit"], bool)
            self.assertIsInstance(item["recommendedForStrength"], bool)
        self.assertTrue(any("duration" in item["trackingCapabilities"] for item in self.items))
        self.assertTrue(any(item["cardioSuitable"] for item in self.items))
        self.assertTrue(any("load" in item["trackingCapabilities"] for item in self.items))

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

    def test_existing_immutable_release_is_refused_before_any_write(self):
        with mock.patch.dict(MODULE.os.environ, {"FIRESTORE_EMULATOR_HOST": "127.0.0.1:8080"}), \
             mock.patch.object(MODULE.urllib.request, "urlopen", return_value=mock.Mock(read=lambda: b"{}")) as opened:
            with self.assertRaisesRegex(ValueError, "immutable release already exists"):
                MODULE.emulator_write("demo-hv1-strength-local", self.release, self.items)
            self.assertEqual(1, opened.call_count)

    def test_missing_emulator_endpoint_fails_closed(self):
        with mock.patch.dict(MODULE.os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "explicit local Firestore emulator"):
                MODULE.emulator_write("demo-hv1-strength-local", self.release, self.items)

    def test_emulator_publication_uses_one_atomic_commit(self):
        not_found = urllib.error.HTTPError("local", 404, "missing", {}, None)
        confirmed = {"writeResults": [{} for _ in range(len(self.items) + 2)]}
        with mock.patch.dict(MODULE.os.environ, {"FIRESTORE_EMULATOR_HOST": "127.0.0.1:8080"}), \
             mock.patch.object(MODULE.urllib.request, "urlopen", side_effect=[not_found, mock.Mock(read=lambda: json.dumps(confirmed).encode())]) as opened:
            MODULE.emulator_write("demo-hv1-strength-local", self.release, self.items)
            request = opened.call_args_list[1].args[0]
            self.assertTrue(request.full_url.endswith(":commit"))


if __name__ == "__main__": unittest.main()
