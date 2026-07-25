import copy
import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[1]
TOOLS = WORKSPACE / "tools" / "catalogue"
sys.path.insert(0, str(TOOLS))

import build_runtime_catalogue as runtime  # noqa: E402

MANIFEST = WORKSPACE / "catalogue" / "candidate-manifest-v2.csv"
REFERENCES = WORKSPACE / "catalogue" / "reference" / "v2"
MAPPING = WORKSPACE / "catalogue" / "runtime" / "canonical-id-map-v1.json"
SCHEMA = (
    WORKSPACE
    / "catalogue"
    / "runtime"
    / "runtime-catalogue-contract-v1.schema.json"
)


class RuntimeCatalogueBuildTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.release = runtime.build_release(
            MANIFEST,
            REFERENCES,
            MAPPING,
            "pilot_staging",
        )

    def write_mapping(self, directory, mutate):
        data = json.loads(MAPPING.read_text(encoding="utf-8"))
        mutate(data)
        path = Path(directory) / "mapping.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def write_manifest(self, directory, mutate):
        with MANIFEST.open(newline="", encoding="utf-8-sig") as handle:
            reader = csv.DictReader(handle)
            rows = list(reader)
            fields = reader.fieldnames
        mutate(rows)
        path = Path(directory) / "manifest.csv"
        with path.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)
        return path

    def exercise_by_id(self, canonical_id):
        return next(
            item
            for item in self.release["exercises"]
            if item["canonical_id"] == canonical_id
        )

    def test_transforms_exact_eight_records(self):
        self.assertEqual(8, self.release["record_count"])
        self.assertEqual(8, len(self.release["exercises"]))
        self.assertEqual(
            {
                "Push-Up",
                "Barbell Bench Press",
                "Dumbbell Goblet Squat",
                "Seated Cable Row",
                "Selectorized Chest Press",
                "Bulgarian Split Squat",
                "Plank",
                "Farmer's Carry",
            },
            {item["display_name"] for item in self.release["exercises"]},
        )

    def test_runtime_contract_and_required_fields(self):
        schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        self.assertEqual(1, schema["properties"]["runtime_contract_version"]["const"])
        self.assertEqual(1, self.release["runtime_contract_version"])
        self.assertEqual("2.0", self.release["schema_version"])
        self.assertEqual("official_catalogue_release", self.release["distribution_scope"])
        for exercise in self.release["exercises"]:
            runtime.validate_runtime_exercise(exercise)

    def test_expected_identity_mapping(self):
        mapping = runtime.load_mapping(MAPPING)["by_key"]
        self.assertEqual("bench_press", mapping["barbell_bench_press"]["canonical_id"])
        self.assertEqual("legacy_seed", mapping["barbell_bench_press"]["identity_source"])
        self.assertEqual("plank", mapping["plank"]["canonical_id"])
        self.assertEqual("legacy_seed", mapping["plank"]["identity_source"])
        self.assertEqual("ex_push_up", mapping["push_up"]["canonical_id"])
        self.assertEqual("new_allocation", mapping["push_up"]["identity_source"])
        self.assertEqual(8, len({value["canonical_id"] for value in mapping.values()}))

    def test_missing_mapping_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.write_mapping(
                directory,
                lambda data: data["entries"].pop(),
            )
            with self.assertRaisesRegex(runtime.RuntimeBuildError, "Missing canonical ID"):
                runtime.load_mapping(path)

    def test_duplicate_canonical_id_is_rejected(self):
        def mutate(data):
            data["entries"][1]["canonical_id"] = data["entries"][0]["canonical_id"]

        with tempfile.TemporaryDirectory() as directory:
            path = self.write_mapping(directory, mutate)
            with self.assertRaisesRegex(runtime.RuntimeBuildError, "Duplicate canonical_id"):
                runtime.load_mapping(path)

    def test_duplicate_catalogue_key_is_rejected(self):
        def mutate(data):
            data["entries"][1]["catalogue_key"] = data["entries"][0]["catalogue_key"]

        with tempfile.TemporaryDirectory() as directory:
            path = self.write_mapping(directory, mutate)
            with self.assertRaisesRegex(runtime.RuntimeBuildError, "Duplicate catalogue_key"):
                runtime.load_mapping(path)

    def test_unknown_runtime_contract_version_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.write_mapping(
                directory,
                lambda data: data.update(runtime_contract_version=2),
            )
            with self.assertRaisesRegex(runtime.RuntimeBuildError, "Unsupported runtime contract"):
                runtime.load_mapping(path)

    def test_production_rejects_current_drafts(self):
        with self.assertRaisesRegex(runtime.RuntimeBuildError, "Production channel rejected"):
            runtime.build_release(MANIFEST, REFERENCES, MAPPING, "production")

    def test_pilot_staging_retains_governance_boundary(self):
        self.assertEqual("pilot_staging", self.release["channel"])
        self.assertNotIn("review_status", json.dumps(self.release))
        self.assertNotIn("ai_review_flags", json.dumps(self.release))
        self.assertNotIn("human_verified_fields", json.dumps(self.release))

    def test_transform_is_byte_deterministic(self):
        second = runtime.build_release(
            MANIFEST,
            REFERENCES,
            MAPPING,
            "pilot_staging",
        )
        self.assertEqual(
            runtime.serialise_release(self.release),
            runtime.serialise_release(second),
        )
        self.assertEqual(self.release["checksum"], second["checksum"])
        ids = [item["canonical_id"] for item in self.release["exercises"]]
        self.assertEqual(sorted(ids), ids)

    def test_runtime_mutation_changes_checksum(self):
        mutated = copy.deepcopy(self.release)
        mutated["exercises"][0]["display_name"] += " Test"
        mutated_payload = {
            key: value
            for key, value in mutated.items()
            if key != "checksum"
        }
        self.assertNotEqual(
            self.release["checksum"],
            runtime.checksum_for(mutated_payload),
        )

    def test_malformed_source_record_is_rejected(self):
        def mutate(rows):
            next(row for row in rows if row["catalogue_key"] == "push_up")[
                "primary_muscles"
            ] = ""

        with tempfile.TemporaryDirectory() as directory:
            manifest = self.write_manifest(directory, mutate)
            with self.assertRaisesRegex(runtime.RuntimeBuildError, "Schema v2 validation"):
                runtime.build_release(
                    manifest,
                    REFERENCES,
                    MAPPING,
                    "pilot_staging",
                )

    def test_unresolved_required_relationship_is_rejected(self):
        def mutate(rows):
            next(row for row in rows if row["catalogue_key"] == "push_up")[
                "parent_exercise_key"
            ] = "missing_parent"

        with tempfile.TemporaryDirectory() as directory:
            manifest = self.write_manifest(directory, mutate)
            with self.assertRaisesRegex(runtime.RuntimeBuildError, "Schema v2 validation"):
                runtime.build_release(
                    manifest,
                    REFERENCES,
                    MAPPING,
                    "pilot_staging",
                )

    def test_external_pilot_relationships_are_omitted(self):
        goblet = self.exercise_by_id("ex_goblet_squat")
        self.assertEqual([], goblet["relationships"])

    def test_taxonomy_and_search_use_runtime_values(self):
        seated_row = self.exercise_by_id("ex_seated_cable_row")
        self.assertEqual("horizontal_pull", seated_row["classification"]["movement_pattern"])
        self.assertEqual("cable_machine", seated_row["equipment"]["required"][0])
        self.assertIn(
            {
                "value": "Low Cable Row",
                "normalised": "low cable row",
                "type": "search",
            },
            seated_row["search"]["aliases"],
        )

    def test_official_release_is_separate_from_custom_exercises(self):
        encoded = runtime.serialise_release(self.release).decode("utf-8")
        self.assertEqual("official_catalogue_release", self.release["distribution_scope"])
        self.assertNotIn("customExercises", encoded)
        self.assertNotIn("humanUserId", encoded)
        self.assertNotIn("isCustom", encoded)


if __name__ == "__main__":
    unittest.main()
