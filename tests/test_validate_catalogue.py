import csv
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "validate_catalogue", WORKSPACE / "tools" / "catalogue" / "validate_catalogue.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class CatalogueValidationTests(unittest.TestCase):
    def test_demonstration_manifest_has_no_errors(self):
        findings, count = MODULE.validate(
            WORKSPACE / "catalogue" / "candidate-manifest.csv",
            WORKSPACE / "catalogue" / "reference",
        )
        self.assertEqual(10, count)
        self.assertEqual([], [finding for finding in findings if finding.severity == "ERROR"])

    def test_duplicate_alias_and_invalid_values_are_detected(self):
        source = WORKSPACE / "catalogue" / "candidate-manifest.csv"
        with source.open(newline="", encoding="utf-8") as handle:
            reader = csv.DictReader(handle)
            row = next(reader)
            fields = reader.fieldnames
        broken = dict(row)
        broken["catalogue_key"] = row["catalogue_key"]
        broken["canonical_name"] = "Push-Up"
        broken["search_aliases"] = row["canonical_name"]
        broken["launch_priority"] = "Urgent"
        broken["equipment"] = "Imaginary Machine"
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "broken.csv"
            with manifest.open("w", newline="", encoding="utf-8") as handle:
                writer = csv.DictWriter(handle, fieldnames=fields)
                writer.writeheader()
                writer.writerow(row)
                writer.writerow(broken)
            findings, _ = MODULE.validate(manifest, WORKSPACE / "catalogue" / "reference")
        codes = {finding.code for finding in findings}
        self.assertTrue({"DUPLICATE_KEY", "CONTROLLED_VALUE", "UNKNOWN_EQUIPMENT", "ALIAS_IS_CANONICAL"} <= codes)

    def test_empty_multi_value_elements_are_detected(self):
        source = WORKSPACE / "catalogue" / "candidate-manifest.csv"
        with source.open(newline="", encoding="utf-8") as handle:
            reader = csv.DictReader(handle)
            row = next(reader)
            fields = reader.fieldnames
        broken = dict(row)
        broken["search_aliases"] = "Cable Row||Low Cable Row"
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "broken.csv"
            with manifest.open("w", newline="", encoding="utf-8") as handle:
                writer = csv.DictWriter(handle, fieldnames=fields)
                writer.writeheader()
                writer.writerow(broken)
            findings, _ = MODULE.validate(manifest, WORKSPACE / "catalogue" / "reference")
        self.assertTrue(
            any(
                finding.severity == "ERROR"
                and finding.code == "EMPTY_MULTI_VALUE"
                and finding.field == "search_aliases"
                for finding in findings
            )
        )


if __name__ == "__main__":
    unittest.main()
