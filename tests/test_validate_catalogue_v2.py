import csv
import importlib.util
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[1]
REFERENCE_SOURCE = WORKSPACE / "catalogue" / "reference" / "v2"
SPEC = importlib.util.spec_from_file_location(
    "validate_catalogue_v2", WORKSPACE / "tools" / "catalogue" / "validate_catalogue.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class CatalogueV2ValidationTests(unittest.TestCase):
    def valid_row(self, **overrides):
        row = {column: "" for column in MODULE.V2_COLUMNS}
        row.update(
            {
                "schema_version": "2.0",
                "catalogue_key": "test_barbell_press",
                "canonical_name": "Test Barbell Press",
                "variation_type": "Base",
                "review_status": "Draft",
                "review_notes": "Test fixture only.",
                "content_origin": "Human Authored",
                "exercise_family": "Chest and Horizontal Pressing",
                "temporary_android_category": "Chest",
                "laterality": "Bilateral",
                "compound_or_isolation": "Compound",
                "exercise_role": "Primary Lift",
                "difficulty": "Beginner",
                "technical_complexity": "Low",
                "facility_tier": "Home Gym",
                "primary_movement_pattern": "Horizontal Push",
                "primary_joint_actions": "Horizontal Adduction|Elbow Extension",
                "support_type": "Bench Supported",
                "torso_position": "Supine",
                "loading_position": "Chest Loaded",
                "grip_type": "Pronated",
                "bench_angle": "Flat",
                "primary_muscles": "Pectoralis Major",
                "secondary_muscles": "Triceps Brachii",
                "equipment": "Barbell|Bench",
                "external_load": "External Load Required",
                "coaching_review_status": "Not Started",
                "clinical_review_status": "Not Required",
                "training_goals": "Hypertrophy",
                "loadability": "High",
            }
        )
        row.update(overrides)
        return row

    def write_manifest(self, directory, rows, columns=None):
        manifest = Path(directory) / "manifest.csv"
        fieldnames = columns or MODULE.V2_COLUMNS
        with manifest.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(rows)
        return manifest

    def validate_rows(self, rows, columns=None, references=REFERENCE_SOURCE):
        with tempfile.TemporaryDirectory() as directory:
            manifest = self.write_manifest(directory, rows, columns)
            return MODULE.validate_v2(manifest, references)

    def codes(self, findings):
        return {finding.code for finding in findings}

    def copy_references(self, directory):
        target = Path(directory) / "references"
        shutil.copytree(REFERENCE_SOURCE, target)
        return target

    def append_reference_row(self, path, row):
        with path.open("a", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=MODULE.REFERENCE_COLUMNS)
            writer.writerow(row)

    def test_valid_empty_v2_manifest(self):
        findings, count = self.validate_rows([])
        self.assertEqual(0, count)
        self.assertEqual([], findings)

    def test_valid_minimal_v2_record(self):
        findings, count = self.validate_rows([self.valid_row()])
        self.assertEqual(1, count)
        self.assertEqual([], findings)

    def test_missing_schema_version(self):
        findings, _ = self.validate_rows([self.valid_row(schema_version="")])
        self.assertIn("MISSING_SCHEMA_VERSION", self.codes(findings))

    def test_wrong_schema_version(self):
        findings, _ = self.validate_rows([self.valid_row(schema_version="1.0")])
        self.assertIn("WRONG_SCHEMA_VERSION", self.codes(findings))

    def test_missing_required_column(self):
        columns = [column for column in MODULE.V2_COLUMNS if column != "primary_muscles"]
        findings, _ = self.validate_rows([self.valid_row()], columns=columns)
        self.assertIn("MISSING_COLUMN", self.codes(findings))

    def test_unknown_controlled_value(self):
        findings, _ = self.validate_rows([self.valid_row(laterality="Sometimes")])
        self.assertIn("CONTROLLED_VALUE", self.codes(findings))

    def test_deprecated_controlled_value_prevents_approval(self):
        with tempfile.TemporaryDirectory() as directory:
            references = self.copy_references(directory)
            path = references / "laterality.csv"
            with path.open(newline="", encoding="utf-8") as handle:
                rows = list(csv.DictReader(handle))
            rows[0]["status"] = "Deprecated"
            with path.open("w", newline="", encoding="utf-8") as handle:
                writer = csv.DictWriter(handle, fieldnames=MODULE.REFERENCE_COLUMNS)
                writer.writeheader()
                writer.writerows(rows)
            manifest = self.write_manifest(directory, [self.valid_row(review_status="Approved")])
            findings, _ = MODULE.validate_v2(manifest, references)
        self.assertIn("DEPRECATED_APPROVED_VALUE", self.codes(findings))

    def test_missing_reference_file(self):
        with tempfile.TemporaryDirectory() as directory:
            references = self.copy_references(directory)
            (references / "muscles.csv").unlink()
            manifest = self.write_manifest(directory, [self.valid_row()])
            findings, _ = MODULE.validate_v2(manifest, references)
        self.assertIn("MISSING_REFERENCE", self.codes(findings))

    def test_duplicate_reference_value(self):
        with tempfile.TemporaryDirectory() as directory:
            references = self.copy_references(directory)
            self.append_reference_row(
                references / "laterality.csv",
                {
                    "value": "Bilateral",
                    "slug": "bilateral_copy",
                    "status": "Active",
                    "description": "Duplicate test value",
                    "notes": "",
                    "sort_order": "999",
                },
            )
            manifest = self.write_manifest(directory, [self.valid_row()])
            findings, _ = MODULE.validate_v2(manifest, references)
        self.assertIn("DUPLICATE_REFERENCE_VALUE", self.codes(findings))

    def test_duplicate_reference_slug(self):
        with tempfile.TemporaryDirectory() as directory:
            references = self.copy_references(directory)
            self.append_reference_row(
                references / "laterality.csv",
                {
                    "value": "Test Laterality",
                    "slug": "bilateral",
                    "status": "Active",
                    "description": "Duplicate test slug",
                    "notes": "",
                    "sort_order": "999",
                },
            )
            manifest = self.write_manifest(directory, [self.valid_row()])
            findings, _ = MODULE.validate_v2(manifest, references)
        self.assertIn("DUPLICATE_REFERENCE_SLUG", self.codes(findings))

    def test_retired_catalogue_key_cannot_be_reused(self):
        with tempfile.TemporaryDirectory() as directory:
            references = self.copy_references(directory)
            self.append_reference_row(
                references / "retired-keys.csv",
                {
                    "value": "test_barbell_press",
                    "slug": "test_barbell_press",
                    "status": "Retired",
                    "description": "Retired test key",
                    "notes": "Reserved permanently for regression coverage",
                    "sort_order": "10",
                },
            )
            manifest = self.write_manifest(directory, [self.valid_row()])
            findings, _ = MODULE.validate_v2(manifest, references)
        self.assertIn("RETIRED_KEY_REUSE", self.codes(findings))

    def test_malformed_multi_value_field(self):
        findings, _ = self.validate_rows([self.valid_row(primary_muscles="Pectoralis Major||Deltoid — Anterior")])
        self.assertIn("EMPTY_MULTI_VALUE", self.codes(findings))

    def test_duplicate_multi_value_item(self):
        findings, _ = self.validate_rows([self.valid_row(primary_muscles="Pectoralis Major|Pectoralis Major")])
        self.assertIn("DUPLICATE_MULTI_VALUE", self.codes(findings))

    def test_alias_collision(self):
        findings, _ = self.validate_rows([self.valid_row(search_aliases="Test Barbell Press")])
        self.assertIn("ALIAS_COLLISION", self.codes(findings))

    def test_unresolved_parent_key(self):
        findings, _ = self.validate_rows([self.valid_row(parent_exercise_key="missing_parent")])
        self.assertIn("UNRESOLVED_REFERENCE", self.codes(findings))

    def test_self_reference(self):
        findings, _ = self.validate_rows([self.valid_row(parent_exercise_key="test_barbell_press")])
        self.assertIn("SELF_REFERENCE", self.codes(findings))

    def test_cyclic_parent_relationship(self):
        first = self.valid_row(catalogue_key="first_press", canonical_name="First Press", parent_exercise_key="second_press")
        second = self.valid_row(catalogue_key="second_press", canonical_name="Second Press", parent_exercise_key="first_press")
        findings, _ = self.validate_rows([first, second])
        self.assertIn("PARENT_CYCLE", self.codes(findings))

    def test_cyclic_progression_relationship(self):
        first = self.valid_row(catalogue_key="first_press", canonical_name="First Press", progression_keys="second_press")
        second = self.valid_row(catalogue_key="second_press", canonical_name="Second Press", progression_keys="first_press")
        findings, _ = self.validate_rows([first, second])
        self.assertIn("PROGRESSION_CYCLE", self.codes(findings))

    def test_muscle_list_overlap(self):
        findings, _ = self.validate_rows([self.valid_row(secondary_muscles="Pectoralis Major")])
        self.assertIn("MUSCLE_OVERLAP", self.codes(findings))

    def test_joint_action_overlap(self):
        findings, _ = self.validate_rows([self.valid_row(secondary_joint_actions="Elbow Extension")])
        self.assertIn("JOINT_ACTION_OVERLAP", self.codes(findings))

    def test_movement_pattern_overlap(self):
        findings, _ = self.validate_rows([self.valid_row(secondary_movement_patterns="Horizontal Push")])
        self.assertIn("MOVEMENT_PATTERN_OVERLAP", self.codes(findings))

    def test_invalid_lateral_raise_classification(self):
        row = self.valid_row(
            catalogue_key="dumbbell_lateral_raise",
            canonical_name="Dumbbell Lateral Raise",
            primary_movement_pattern="Vertical Push",
            primary_joint_actions="Trunk Lateral Flexion",
            support_type="Unsupported",
            torso_position="Standing Upright",
            loading_position="At Sides",
            grip_type="Neutral",
            bench_angle="Not Applicable",
            primary_muscles="Deltoid — Lateral",
            secondary_muscles="",
            equipment="Dumbbell",
        )
        findings, _ = self.validate_rows([row])
        self.assertIn("LATERAL_RAISE_ACTION", self.codes(findings))

    def test_missing_bench_for_bench_angle(self):
        findings, _ = self.validate_rows([self.valid_row(equipment="Barbell")])
        self.assertIn("BENCH_REQUIRED", self.codes(findings))

    def test_missing_landmine_equipment_pair(self):
        row = self.valid_row(
            support_type="Unsupported",
            torso_position="Standing Upright",
            loading_position="At Sides",
            bench_angle="Not Applicable",
            grip_type="Neutral",
            equipment="Landmine",
        )
        findings, _ = self.validate_rows([row])
        self.assertIn("LANDMINE_EQUIPMENT", self.codes(findings))

    def test_ai_generated_approved_record(self):
        row = self.valid_row(
            review_status="Approved",
            content_origin="AI Generated — Review Pending",
            ai_assistance_tasks="Taxonomy Suggestion",
        )
        findings, _ = self.validate_rows([row])
        self.assertIn("AI_APPROVAL", self.codes(findings))

    def test_ai_assisted_record_without_assistance_tasks(self):
        row = self.valid_row(content_origin="Human Authored with AI Assistance")
        findings, _ = self.validate_rows([row])
        self.assertIn("AI_TASKS_REQUIRED", self.codes(findings))

    def test_ai_generated_draft_coaching_can_await_review(self):
        row = self.valid_row(
            content_origin="AI Generated — Review Pending",
            ai_assistance_tasks="Coaching Draft",
            execution_cues="Press with controlled intent.",
            coaching_review_status="Required",
        )
        findings, _ = self.validate_rows([row])
        self.assertNotIn("AI_COACHING_REVIEW", self.codes(findings))

    def test_approved_record_with_outstanding_ai_review_flag(self):
        row = self.valid_row(review_status="Approved", ai_review_flags="Anatomy Review Required")
        findings, _ = self.validate_rows([row])
        self.assertIn("AI_REVIEW_FLAGS", self.codes(findings))

    def test_approved_record_with_incomplete_coaching_review(self):
        row = self.valid_row(review_status="Approved", coaching_review_status="Required")
        findings, _ = self.validate_rows([row])
        self.assertIn("COACHING_APPROVAL", self.codes(findings))

    def test_clinical_claim_without_review_or_provenance(self):
        row = self.valid_row(
            contraindication_flags="Clinical Review Required",
            clinical_review_status="Not Required",
            source_provenance="",
        )
        findings, _ = self.validate_rows([row])
        self.assertTrue({"CLINICAL_PROVENANCE", "CLINICAL_REVIEW"} <= self.codes(findings))


if __name__ == "__main__":
    unittest.main()
