import csv
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[1]
TOOLS = WORKSPACE / "tools" / "catalogue"
sys.path.insert(0, str(TOOLS))

from measurement_semantics import validate_measurement_semantics  # noqa: E402

REFERENCES = WORKSPACE / "catalogue" / "reference" / "v2"
MEASUREMENT_SOURCE = WORKSPACE / "catalogue" / "measurement"
CATALOGUE_KEYS = {
    row["catalogue_key"]
    for row in csv.DictReader(
        (WORKSPACE / "catalogue" / "candidate-manifest-v2.csv").open(
            newline="", encoding="utf-8-sig"
        )
    )
}


class MeasurementSemanticsValidationTests(unittest.TestCase):
    def validate(self, mutate=None, catalogue_keys=None, required=None):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "measurement"
            shutil.copytree(MEASUREMENT_SOURCE, target)
            if mutate:
                mutate(target)
            return validate_measurement_semantics(
                catalogue_keys or CATALOGUE_KEYS,
                REFERENCES,
                target / "measurement-modes-v1.csv",
                target / "measurement-mode-fields-v1.csv",
                target / "measurement-mode-derived-v1.csv",
                required_catalogue_keys=required,
            )

    def mutate_rows(self, path, mutate):
        with path.open(newline="", encoding="utf-8-sig") as handle:
            reader = csv.DictReader(handle)
            rows = list(reader)
            fields = reader.fieldnames
        mutate(rows)
        with path.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)

    def codes(self, findings):
        return {finding.code for finding in findings}

    def test_current_measurement_authoring_is_valid(self):
        findings, modes = self.validate(required=CATALOGUE_KEYS)
        self.assertEqual([], findings)
        self.assertEqual(CATALOGUE_KEYS, set(modes))
        self.assertEqual(3, len(modes["pull_up"]))
        self.assertEqual(1, sum(mode.is_default for mode in modes["pull_up"]))

    def test_unknown_measurement_is_rejected(self):
        def mutate(directory):
            self.mutate_rows(
                directory / "measurement-mode-fields-v1.csv",
                lambda rows: rows.__setitem__(
                    0, {**rows[0], "measurement": "unknown_measurement"}
                ),
            )
        findings, _ = self.validate(mutate)
        self.assertIn("UNKNOWN_MEASUREMENT", self.codes(findings))

    def test_duplicate_mode_id_is_rejected(self):
        def mutate(directory):
            self.mutate_rows(
                directory / "measurement-modes-v1.csv",
                lambda rows: rows.append(dict(rows[0])),
            )
        findings, _ = self.validate(mutate)
        self.assertIn("DUPLICATE_MODE_ID", self.codes(findings))

    def test_missing_and_multiple_defaults_are_rejected(self):
        def no_default(directory):
            self.mutate_rows(
                directory / "measurement-modes-v1.csv",
                lambda rows: [
                    row.update(is_default="false")
                    for row in rows if row["catalogue_key"] == "pull_up"
                ],
            )
        findings, _ = self.validate(no_default)
        self.assertIn("NO_DEFAULT_MODE", self.codes(findings))

        def multiple(directory):
            self.mutate_rows(
                directory / "measurement-modes-v1.csv",
                lambda rows: [
                    row.update(is_default="true")
                    for row in rows if row["catalogue_key"] == "pull_up"
                ],
            )
        findings, _ = self.validate(multiple)
        self.assertIn("MULTIPLE_DEFAULT_MODES", self.codes(findings))

    def test_duplicate_measurement_and_invalid_unit_are_rejected(self):
        def duplicate(directory):
            self.mutate_rows(
                directory / "measurement-mode-fields-v1.csv",
                lambda rows: rows.append(dict(rows[0])),
            )
        findings, _ = self.validate(duplicate)
        self.assertIn("DUPLICATE_MEASUREMENT", self.codes(findings))

        def invalid_unit(directory):
            self.mutate_rows(
                directory / "measurement-mode-fields-v1.csv",
                lambda rows: rows[0].update(canonical_unit="seconds"),
            )
        findings, _ = self.validate(invalid_unit)
        self.assertIn("INVALID_MEASUREMENT_UNIT", self.codes(findings))

    def test_load_and_assistance_semantics_require_matching_measurements(self):
        def missing_load(directory):
            self.mutate_rows(
                directory / "measurement-mode-fields-v1.csv",
                lambda rows: rows.__setitem__(
                    slice(None),
                    [
                        row for row in rows
                        if not (
                            row["catalogue_key"] == "barbell_bench_press"
                            and row["measurement"] == "load"
                        )
                    ],
                ),
            )
        findings, _ = self.validate(missing_load)
        self.assertIn("LOAD_SEMANTICS_WITHOUT_LOAD", self.codes(findings))

        def missing_assistance(directory):
            self.mutate_rows(
                directory / "measurement-mode-fields-v1.csv",
                lambda rows: rows.__setitem__(
                    slice(None),
                    [
                        row for row in rows
                        if not (
                            row["catalogue_key"] == "pull_up"
                            and row["mode_id"] == "assisted_reps"
                            and row["measurement"] == "assistance"
                        )
                    ],
                ),
            )
        findings, _ = self.validate(missing_assistance)
        self.assertIn("ASSISTANCE_WITHOUT_MEASUREMENT", self.codes(findings))

    def test_derived_pace_requires_distance_and_duration(self):
        def mutate(directory):
            self.mutate_rows(
                directory / "measurement-mode-derived-v1.csv",
                lambda rows: rows.append(
                    {
                        "catalogue_key": "plank",
                        "mode_id": "timed_hold",
                        "derived_metric": "pace",
                    }
                ),
            )
        findings, _ = self.validate(mutate)
        self.assertIn("INVALID_DERIVED_METRIC", self.codes(findings))

    def test_empty_mode_and_unknown_load_semantics_are_rejected(self):
        def empty_mode(directory):
            self.mutate_rows(
                directory / "measurement-modes-v1.csv",
                lambda rows: rows.append(
                    {
                        "measurement_schema_version": "1",
                        "catalogue_key": "side_plank",
                        "mode_id": "empty",
                        "is_default": "true",
                        "load_semantics": "bodyweight",
                        "min_runtime_contract_version": "2",
                    }
                ),
            )
        findings, _ = self.validate(empty_mode)
        self.assertIn("EMPTY_MEASUREMENT_MODE", self.codes(findings))

        def unknown_semantics(directory):
            self.mutate_rows(
                directory / "measurement-modes-v1.csv",
                lambda rows: rows[0].update(load_semantics="mystery"),
            )
        findings, _ = self.validate(unknown_semantics)
        self.assertIn("UNKNOWN_LOAD_SEMANTICS", self.codes(findings))

    def test_downward_dog_timed_hold_is_governed_catalogue_data(self):
        findings, modes = self.validate()
        self.assertEqual([], findings)
        mode = modes["downward_dog"][0]
        self.assertTrue(mode.is_default)
        self.assertEqual({"duration", "rpe"}, {field.measurement for field in mode.fields})

    def test_continuous_cardio_modes_are_explicit_and_derived(self):
        findings, modes = self.validate()
        self.assertEqual([], findings)
        for key in ("running", "treadmill_run", "row_erg"):
            default = next(mode for mode in modes[key] if mode.is_default)
            self.assertEqual("distance_duration", default.mode_id)
            self.assertEqual(
                {"duration", "distance"},
                {
                    field.measurement
                    for field in default.fields
                    if field.requirement == "required"
                },
            )
            self.assertEqual({"pace", "speed"}, set(default.derived_metrics))

    def test_burpee_supports_repetitions_or_timed_work_without_protocol_data(self):
        findings, modes = self.validate()
        self.assertEqual([], findings)
        self.assertEqual({"repetitions", "timed_work"}, {
            mode.mode_id for mode in modes["burpee"]
        })
        self.assertNotIn("recovery", {
            field.measurement
            for mode in modes["burpee"]
            for field in mode.fields
        })

    def test_representative_semantics_are_not_inferred_from_classification(self):
        findings, modes = self.validate()
        self.assertEqual([], findings)
        expected_defaults = {
            "barbell_bench_press": "external_load_reps",
            "pull_up": "bodyweight_reps",
            "plank": "timed_hold",
            "farmers_carry": "loaded_distance",
            "bulgarian_split_squat": "bodyweight_reps",
        }
        for key, expected in expected_defaults.items():
            self.assertEqual(
                expected,
                next(mode.mode_id for mode in modes[key] if mode.is_default),
            )


if __name__ == "__main__":
    unittest.main()
