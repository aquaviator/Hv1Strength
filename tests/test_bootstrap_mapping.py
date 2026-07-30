import csv
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[1]
TOOLS = WORKSPACE / "tools" / "catalogue"
sys.path.insert(0, str(TOOLS))

from validate_bootstrap_mapping import (  # noqa: E402
    EXPECTED_LEGACY_SEEDS,
    validate_bootstrap_mapping,
)

MAPPING = WORKSPACE / "catalogue" / "mapping" / "strength-bootstrap-mapping-v1.json"
MANIFEST = WORKSPACE / "catalogue" / "candidate-manifest-v2.csv"
LEDGER = WORKSPACE / "catalogue" / "runtime" / "canonical-id-map-v1.json"
RETIRED = WORKSPACE / "catalogue" / "reference" / "v2" / "retired-keys.csv"


class BootstrapMappingTests(unittest.TestCase):
    def validate(self, mutate_mapping=None, mutate_manifest=None, mutate_retired=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            mapping = root / "mapping.json"
            manifest = root / "manifest.csv"
            retired = root / "retired.csv"
            data = json.loads(MAPPING.read_text(encoding="utf-8"))
            if mutate_mapping:
                mutate_mapping(data)
            mapping.write_text(json.dumps(data), encoding="utf-8")

            with MANIFEST.open(newline="", encoding="utf-8-sig") as handle:
                reader = csv.DictReader(handle)
                rows, columns = list(reader), reader.fieldnames
            if mutate_manifest:
                mutate_manifest(rows)
            with manifest.open("w", newline="", encoding="utf-8") as handle:
                writer = csv.DictWriter(handle, fieldnames=columns)
                writer.writeheader()
                writer.writerows(rows)

            shutil.copyfile(RETIRED, retired)
            if mutate_retired:
                mutate_retired(retired)
            return validate_bootstrap_mapping(mapping, manifest, LEDGER, retired)

    @staticmethod
    def codes(findings):
        return {finding.code for finding in findings}

    def test_governed_mapping_contains_every_legacy_seed_once(self):
        findings = self.validate()
        self.assertEqual([], findings)
        data = json.loads(MAPPING.read_text(encoding="utf-8"))
        self.assertEqual(21, len(data["entries"]))
        self.assertEqual(
            set(EXPECTED_LEGACY_SEEDS),
            {entry["legacy_seed_id"] for entry in data["entries"]},
        )

    def test_missing_and_duplicate_legacy_seed_are_rejected(self):
        def mutate(data):
            data["entries"].pop()
            data["entries"].append(dict(data["entries"][0]))

        codes = self.codes(self.validate(mutate_mapping=mutate))
        self.assertIn("DUPLICATE_LEGACY_ID", codes)
        self.assertIn("MISSING_LEGACY_SEEDS", codes)

    def test_unresolved_mapping_must_be_explicit(self):
        def mutate(data):
            entry = next(item for item in data["entries"] if item["legacy_seed_id"] == "chest_fly")
            entry["relationship"] = "EXACT_IDENTITY"

        self.assertIn("UNRESOLVED_MAPPING", self.codes(self.validate(mutate_mapping=mutate)))

    def test_canonical_id_must_match_ledger_and_cannot_be_reused(self):
        def mutate(data):
            entry = next(item for item in data["entries"] if item["legacy_seed_id"] == "plank")
            entry["canonical_id"] = "bench_press"

        codes = self.codes(self.validate(mutate_mapping=mutate))
        self.assertIn("CANONICAL_LEDGER_MISMATCH", codes)
        self.assertIn("CANONICAL_ID_REUSE", codes)

    def test_approved_mapping_cannot_target_draft_or_retired_record(self):
        def mapping(data):
            entry = next(item for item in data["entries"] if item["legacy_seed_id"] == "bench_press")
            entry["mapping_status"] = "APPROVED"

        def manifest(rows):
            next(row for row in rows if row["catalogue_key"] == "barbell_bench_press")[
                "review_status"
            ] = "Approved"

        def retired(path):
            with path.open("a", encoding="utf-8") as handle:
                handle.write(
                    "barbell_bench_press,barbell_bench_press,Retired,"
                    "Test retirement evidence,,10\n"
                )

        self.assertIn(
            "APPROVED_RETIRED",
            self.codes(self.validate(mapping, manifest, retired)),
        )

    def test_legacy_display_name_is_evidence_not_runtime_identity(self):
        def mutate(data):
            entry = next(item for item in data["entries"] if item["legacy_seed_id"] == "pull_up")
            entry["legacy_display_name"] = "Renamed presentation"

        self.assertIn(
            "LEGACY_NAME_EVIDENCE",
            self.codes(self.validate(mutate_mapping=mutate)),
        )
        self.assertIn("pull_up", EXPECTED_LEGACY_SEEDS)


if __name__ == "__main__":
    unittest.main()
