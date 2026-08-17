import copy, importlib.util, json, os, pathlib, sys, unittest
from unittest import mock

ROOT = pathlib.Path(__file__).parents[1]
SPEC = importlib.util.spec_from_file_location("governed_catalogue", ROOT / "tools/governed_catalogue.py")
MODULE = importlib.util.module_from_spec(SPEC); sys.modules[SPEC.name] = MODULE; SPEC.loader.exec_module(MODULE)


class FakeStore:
    def __init__(self): self.documents, self.events, self.fail_after = {}, [], None
    def get(self, path):
        value = self.documents.get(path)
        return None if value is None else MODULE.StoredDocument(copy.deepcopy(value), "time-1")
    def list(self, path):
        prefix = path + "/"
        return [MODULE.StoredDocument(copy.deepcopy(v), "time-1") for k, v in sorted(self.documents.items()) if k.startswith(prefix) and "/" not in k[len(prefix):]]
    def create_many(self, documents):
        self.events.append(("create", [p for p, _ in documents]))
        if self.fail_after is not None and len(self.events) > self.fail_after: raise RuntimeError("injected failure")
        if any(path in self.documents for path, _ in documents): raise ValueError("already exists")
        self.documents.update({path: copy.deepcopy(fields) for path, fields in documents})
    def replace(self, path, fields, update_time=None):
        self.events.append(("replace", [path])); self.documents[path] = copy.deepcopy(fields)


class GovernedCatalogueTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = json.loads((ROOT / "app/src/main/assets/strength-exercise-catalogue.json").read_text(encoding="utf-8"))
        cls.release, cls.items = MODULE.build_release(cls.source, "test-release", "2026-08-14T00:00:00Z")

    def populated(self):
        store = FakeStore(); MODULE.publish_release(store, self.release, self.items); return store

    def test_01_deterministic_generation(self):
        self.assertEqual((self.release, self.items), MODULE.build_release(self.source, "test-release", "2026-08-14T00:00:00Z"))
        self.assertEqual(264, len(self.items)); self.assertEqual(self.release["contentSha256"], MODULE.sha256(self.items))

    def test_02_original_ids_and_app_neutral_schema(self):
        self.assertEqual({x["id"] for x in self.source["exercises"]}, {x["exerciseId"] for x in self.items})
        for item in self.items: self.assertNotIn("humanUserId", item); self.assertNotIn("firebaseUid", item)

    def test_03_publication_excludes_pointer_and_unrelated_paths(self):
        store = self.populated(); paths = set(store.documents)
        self.assertNotIn("exercise_catalogue/current", paths)
        self.assertFalse(any(p.startswith(("staging_exercises/", "users/", "accounts/")) for p in paths))

    def test_04_activation_writes_only_pointer(self):
        store = self.populated(); store.events.clear()
        MODULE.activate_release(store, "test-release", self.release["catalogueVersion"], 264, self.release["contentSha256"], "now")
        self.assertEqual([("create", ["exercise_catalogue/current"])], store.events)

    def test_05_new_publication_is_final_and_verified(self):
        store = FakeStore(); receipt = MODULE.publish_release(store, self.release, self.items)
        self.assertTrue(receipt["published"]); self.assertFalse(receipt["activated"])
        self.assertEqual("published", store.documents["exercise_catalogue_releases/test-release"]["status"])

    def test_06_identical_publication_is_idempotent(self):
        store = self.populated(); before = copy.deepcopy(store.documents); receipt = MODULE.publish_release(store, self.release, self.items)
        self.assertTrue(receipt["idempotent"]); self.assertEqual(before, store.documents)

    def test_07_different_existing_content_is_rejected(self):
        store = self.populated(); store.documents["exercise_catalogue_releases/test-release"]["editorialNotes"] = "different"
        with self.assertRaisesRegex(ValueError, "metadata differs"): MODULE.publish_release(store, self.release, self.items)

    def test_08_incomplete_release_rejected_for_activation(self):
        store = FakeStore(); store.documents["exercise_catalogue_releases/test-release"] = {**self.release, "status": "publishing", "validationStatus": "incomplete"}
        with self.assertRaisesRegex(ValueError, "metadata mismatch"): MODULE.activate_release(store, "test-release", self.release["catalogueVersion"], 264, self.release["contentSha256"], "now")

    def test_09_wrong_count_rejected(self):
        with self.assertRaisesRegex(ValueError, "metadata mismatch"): MODULE.verify_release(self.populated(), "test-release", self.release["catalogueVersion"], 263, self.release["contentSha256"])

    def test_10_wrong_checksum_rejected(self):
        with self.assertRaisesRegex(ValueError, "metadata mismatch"): MODULE.verify_release(self.populated(), "test-release", self.release["catalogueVersion"], 264, "0" * 64)

    def test_11_missing_release_rejected(self):
        with self.assertRaisesRegex(ValueError, "missing"): MODULE.verify_release(FakeStore(), "missing", self.release["catalogueVersion"], 264, self.release["contentSha256"])

    def test_12_already_current_is_idempotent(self):
        store = self.populated(); MODULE.activate_release(store, "test-release", self.release["catalogueVersion"], 264, self.release["contentSha256"], "one")
        before, events = copy.deepcopy(store.documents), len(store.events)
        receipt = MODULE.activate_release(store, "test-release", self.release["catalogueVersion"], 264, self.release["contentSha256"], "two")
        self.assertTrue(receipt["idempotent"]); self.assertEqual(before, store.documents); self.assertEqual(events, len(store.events))

    def test_13_pointer_moves_only_after_verification(self):
        store = self.populated(); before = copy.deepcopy(store.documents)
        with self.assertRaises(ValueError): MODULE.activate_release(store, "test-release", self.release["catalogueVersion"], 264, "f" * 64, "now")
        self.assertEqual(before, store.documents)

    def test_14_rollback_changes_only_pointer(self):
        store = self.populated(); second = {**self.release, "releaseId": "second"}; MODULE.publish_release(store, second, self.items)
        MODULE.activate_release(store, "second", second["catalogueVersion"], 264, second["contentSha256"], "one")
        immutable = {k: copy.deepcopy(v) for k, v in store.documents.items() if k != "exercise_catalogue/current"}; store.events.clear()
        receipt = MODULE.activate_release(store, "test-release", self.release["catalogueVersion"], 264, self.release["contentSha256"], "two")
        self.assertEqual("second", receipt["previousReleaseId"]); self.assertEqual([("replace", ["exercise_catalogue/current"])], store.events)
        self.assertEqual(immutable, {k: v for k, v in store.documents.items() if k != "exercise_catalogue/current"})

    def test_15_partial_multi_batch_never_appears_complete(self):
        old_limit = MODULE.MAX_COMMIT_WRITES; MODULE.MAX_COMMIT_WRITES = 100
        try:
            store = FakeStore(); store.fail_after = 2
            with self.assertRaisesRegex(RuntimeError, "injected"): MODULE.publish_release(store, self.release, self.items)
            self.assertEqual("publishing", store.documents["exercise_catalogue_releases/test-release"]["status"])
            self.assertNotIn("exercise_catalogue/current", store.documents)
        finally: MODULE.MAX_COMMIT_WRITES = old_limit

    def test_16_partial_existing_release_fails_closed(self):
        store = FakeStore(); store.documents["exercise_catalogue_releases/test-release"] = {**self.release, "status": "publishing"}
        with self.assertRaisesRegex(ValueError, "partial"): MODULE.publish_release(store, self.release, self.items)

    def test_17_demo_guard_requires_loopback_emulator(self):
        args = type("A", (), {"project": "demo-test"})()
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "local Firestore emulator"): MODULE._store(args)
        with mock.patch.dict(os.environ, {"FIRESTORE_EMULATOR_HOST": "127.0.0.1:8080"}): self.assertIsInstance(MODULE._store(args), MODULE.FirestoreRestStore)

    def test_18_production_requires_exact_project_confirmation_and_credentials(self):
        for project in ("other-project", "hv1-platform-wildcard"):
            with self.assertRaises(ValueError): MODULE._store(type("A", (), {"project": project, "confirm_production": True})())
        args = type("A", (), {"project": "hv1-platform", "confirm_production": False})()
        with self.assertRaisesRegex(ValueError, "confirmation"): MODULE._store(args)
        args.confirm_production = True
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "token"): MODULE._store(args)

    def test_19_publish_cannot_call_activation(self):
        store = FakeStore()
        with mock.patch.object(MODULE, "activate_release", side_effect=AssertionError("must not run")):
            MODULE.publish_release(store, self.release, self.items)

    def test_20_schema_failures_and_alias_collisions_are_rejected(self):
        bad = [dict(x) for x in self.items]; bad[0] = dict(bad[0], relatedExerciseIds=[bad[0]["exerciseId"]], trackingCapabilities=["assisted_load"])
        self.assertTrue(any("self reference" in x for x in MODULE.validate_exercises(bad)))
        bad = [dict(x) for x in self.items]; bad[1] = dict(bad[1], aliases=[bad[0]["canonicalName"]])
        self.assertTrue(any("collision" in x for x in MODULE.validate_exercises(bad)))


@unittest.skipUnless(os.environ.get("FIRESTORE_EMULATOR_HOST") in {"127.0.0.1:8080", "localhost:8080"}, "local emulator not running")
class GovernedCatalogueEmulatorIntegrationTest(unittest.TestCase):
    def test_publish_verify_activate_and_rollback(self):
        source = json.loads((ROOT / "app/src/main/assets/strength-exercise-catalogue.json").read_text(encoding="utf-8"))
        release, items = MODULE.build_release(source, "integration-one", "2026-08-14T00:00:00Z")
        second = {**release, "releaseId": "integration-two"}
        store = MODULE.FirestoreRestStore("demo-hv1-strength-local", os.environ["FIRESTORE_EMULATOR_HOST"], None)
        MODULE.publish_release(store, release, items); self.assertIsNone(store.get("exercise_catalogue/current"))
        MODULE.verify_release(store, release["releaseId"], release["catalogueVersion"], len(items), release["contentSha256"])
        MODULE.publish_release(store, second, items)
        MODULE.activate_release(store, second["releaseId"], second["catalogueVersion"], len(items), second["contentSha256"], "one")
        before = {x.fields["exerciseId"]: x.fields for x in store.list("exercise_catalogue_releases/integration-two/exercises")}
        MODULE.activate_release(store, release["releaseId"], release["catalogueVersion"], len(items), release["contentSha256"], "two")
        after = {x.fields["exerciseId"]: x.fields for x in store.list("exercise_catalogue_releases/integration-two/exercises")}
        self.assertEqual(before, after)


if __name__ == "__main__": unittest.main()
