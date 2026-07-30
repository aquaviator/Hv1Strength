"""Governed authoring and validation for exercise measurement semantics."""

from __future__ import annotations

import csv
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

MEASUREMENT_SCHEMA_VERSION = 1
RUNTIME_CONTRACT_VERSION = 2
MODE_COLUMNS = (
    "measurement_schema_version",
    "catalogue_key",
    "mode_id",
    "is_default",
    "load_semantics",
    "min_runtime_contract_version",
)
FIELD_COLUMNS = (
    "catalogue_key",
    "mode_id",
    "measurement",
    "requirement",
    "canonical_unit",
)
DERIVED_COLUMNS = ("catalogue_key", "mode_id", "derived_metric")
REFERENCE_COLUMNS = ("value", "slug", "status", "description", "notes", "sort_order")
REQUIREMENTS = {"required", "optional"}
MODE_ID_PATTERN = re.compile(r"^[a-z0-9]+(?:_[a-z0-9]+)*$")

CANONICAL_UNIT_BY_MEASUREMENT = {
    "reps": "count",
    "load": "kilograms",
    "duration": "seconds",
    "distance": "metres",
    "rpe": "rpe_scale",
    "assistance": "kilograms",
    "calories": "kilocalories",
    "power": "watts",
    "cadence": "repetitions_per_minute",
    "heart_rate": "beats_per_minute",
    "resistance": "resistance_level",
    "speed": "metres_per_second",
    "pace": "seconds_per_metre",
    "count": "count",
    "vertical_distance": "metres",
}
DERIVATION_REQUIREMENTS = {
    "pace": {"distance", "duration"},
    "speed": {"distance", "duration"},
}


@dataclass(frozen=True)
class MeasurementFinding:
    severity: str
    code: str
    row: int | None
    field: str
    message: str


@dataclass(frozen=True)
class MeasurementField:
    measurement: str
    requirement: str
    canonical_unit: str


@dataclass(frozen=True)
class MeasurementMode:
    catalogue_key: str
    mode_id: str
    is_default: bool
    load_semantics: str
    min_runtime_contract_version: int
    fields: tuple[MeasurementField, ...]
    derived_metrics: tuple[str, ...]


def _read_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        return list(reader.fieldnames or []), list(reader)


def _active_slugs(path: Path) -> set[str]:
    columns, rows = _read_csv(path)
    if columns != list(REFERENCE_COLUMNS):
        raise ValueError(
            f"{path.name} must use governed reference columns: {', '.join(REFERENCE_COLUMNS)}"
        )
    values = [row["value"].strip() for row in rows]
    slugs = [row["slug"].strip() for row in rows]
    if any(not value for value in values) or any(not slug for slug in slugs):
        raise ValueError(f"{path.name} contains a blank governed value or slug")
    if len(values) != len(set(values)):
        raise ValueError(f"{path.name} contains a duplicate governed value")
    if len(slugs) != len(set(slugs)):
        raise ValueError(f"{path.name} contains a duplicate governed slug")
    allowed_statuses = {"Active", "Caution", "Deprecated", "Retired"}
    unknown_statuses = {
        row.get("status", "").strip()
        for row in rows
        if row.get("status", "").strip() not in allowed_statuses
    }
    if unknown_statuses:
        raise ValueError(
            f"{path.name} contains unsupported lifecycle status(es): "
            + ", ".join(sorted(unknown_statuses))
        )
    return {
        row["slug"].strip()
        for row in rows
        if row.get("status", "").strip() in {"Active", "Caution", "Deprecated"}
    }


def validate_measurement_semantics(
    catalogue_keys: set[str],
    references: Path,
    modes_path: Path,
    fields_path: Path,
    derived_path: Path,
    required_catalogue_keys: set[str] | None = None,
) -> tuple[list[MeasurementFinding], dict[str, tuple[MeasurementMode, ...]]]:
    findings: list[MeasurementFinding] = []

    try:
        mode_columns, mode_rows = _read_csv(modes_path)
        field_columns, field_rows = _read_csv(fields_path)
        derived_columns, derived_rows = _read_csv(derived_path)
        measurement_slugs = _active_slugs(references / "measurements.csv")
        unit_slugs = _active_slugs(references / "measurement-units.csv")
        load_semantics_slugs = _active_slugs(references / "load-semantics.csv")
        derived_slugs = _active_slugs(references / "derived-metrics.csv")
    except (OSError, csv.Error, KeyError, ValueError) as exc:
        return [
            MeasurementFinding("ERROR", "MEASUREMENT_INPUT", None, "", str(exc))
        ], {}

    for actual, expected, label in (
        (mode_columns, MODE_COLUMNS, "measurement modes"),
        (field_columns, FIELD_COLUMNS, "measurement fields"),
        (derived_columns, DERIVED_COLUMNS, "derived metrics"),
    ):
        if actual != list(expected):
            findings.append(
                MeasurementFinding(
                    "ERROR",
                    "MEASUREMENT_COLUMNS",
                    None,
                    label,
                    f"Expected columns {list(expected)!r}; found {actual!r}.",
                )
            )
    if findings:
        return findings, {}

    raw_modes: dict[tuple[str, str], dict[str, object]] = {}
    modes_by_exercise: defaultdict[str, list[tuple[str, str]]] = defaultdict(list)

    for row_number, row in enumerate(mode_rows, start=2):
        key = row["catalogue_key"].strip()
        mode_id = row["mode_id"].strip()
        identity = (key, mode_id)

        if not key or not mode_id:
            findings.append(MeasurementFinding("ERROR", "MALFORMED_MODE", row_number, "mode_id", "Catalogue key and mode ID are required."))
            continue
        if key not in catalogue_keys:
            findings.append(MeasurementFinding("ERROR", "UNKNOWN_EXERCISE", row_number, "catalogue_key", f"Unknown catalogue key: {key!r}."))
        if not MODE_ID_PATTERN.fullmatch(mode_id):
            findings.append(MeasurementFinding("ERROR", "MALFORMED_MODE_ID", row_number, "mode_id", f"Invalid stable mode ID: {mode_id!r}."))
        if identity in raw_modes:
            findings.append(MeasurementFinding("ERROR", "DUPLICATE_MODE_ID", row_number, "mode_id", f"Duplicate mode {key}/{mode_id}."))
            continue

        default_text = row["is_default"].strip().lower()
        if default_text not in {"true", "false"}:
            findings.append(MeasurementFinding("ERROR", "MALFORMED_DEFAULT", row_number, "is_default", "is_default must be true or false."))
        schema_text = row["measurement_schema_version"].strip()
        runtime_text = row["min_runtime_contract_version"].strip()
        try:
            schema_version = int(schema_text)
            min_runtime = int(runtime_text)
        except ValueError:
            schema_version = -1
            min_runtime = -1
            findings.append(MeasurementFinding("ERROR", "MALFORMED_VERSION", row_number, "measurement_schema_version", "Schema and runtime versions must be integers."))
        if schema_version != MEASUREMENT_SCHEMA_VERSION:
            findings.append(MeasurementFinding("ERROR", "UNSUPPORTED_MEASUREMENT_SCHEMA", row_number, "measurement_schema_version", f"Expected measurement schema {MEASUREMENT_SCHEMA_VERSION}."))
        if min_runtime != RUNTIME_CONTRACT_VERSION:
            findings.append(MeasurementFinding("ERROR", "UNSUPPORTED_RUNTIME_CONTRACT", row_number, "min_runtime_contract_version", f"Expected runtime contract {RUNTIME_CONTRACT_VERSION}."))

        load_semantics = row["load_semantics"].strip()
        if load_semantics not in load_semantics_slugs:
            findings.append(MeasurementFinding("ERROR", "UNKNOWN_LOAD_SEMANTICS", row_number, "load_semantics", f"Unknown load semantics: {load_semantics!r}."))

        raw_modes[identity] = {
            "catalogue_key": key,
            "mode_id": mode_id,
            "is_default": default_text == "true",
            "load_semantics": load_semantics,
            "min_runtime_contract_version": min_runtime,
            "fields": [],
            "derived": [],
            "row": row_number,
        }
        modes_by_exercise[key].append(identity)

    seen_fields: set[tuple[str, str, str]] = set()
    for row_number, row in enumerate(field_rows, start=2):
        key = row["catalogue_key"].strip()
        mode_id = row["mode_id"].strip()
        measurement = row["measurement"].strip()
        requirement = row["requirement"].strip()
        unit = row["canonical_unit"].strip()
        identity = (key, mode_id)
        field_identity = (key, mode_id, measurement)

        if identity not in raw_modes:
            findings.append(MeasurementFinding("ERROR", "UNKNOWN_MODE_REFERENCE", row_number, "mode_id", f"Measurement field references unknown mode {key}/{mode_id}."))
            continue
        if not measurement or not requirement or not unit:
            findings.append(MeasurementFinding("ERROR", "MALFORMED_MEASUREMENT", row_number, "measurement", "Measurement, requirement and canonical unit are required."))
            continue
        if measurement not in measurement_slugs:
            findings.append(MeasurementFinding("ERROR", "UNKNOWN_MEASUREMENT", row_number, "measurement", f"Unknown measurement: {measurement!r}."))
        if requirement not in REQUIREMENTS:
            findings.append(MeasurementFinding("ERROR", "UNKNOWN_REQUIREMENT", row_number, "requirement", f"Unknown requirement: {requirement!r}."))
        if unit not in unit_slugs:
            findings.append(MeasurementFinding("ERROR", "UNKNOWN_UNIT", row_number, "canonical_unit", f"Unknown canonical unit: {unit!r}."))
        expected_unit = CANONICAL_UNIT_BY_MEASUREMENT.get(measurement)
        if expected_unit is not None and unit != expected_unit:
            findings.append(MeasurementFinding("ERROR", "INVALID_MEASUREMENT_UNIT", row_number, "canonical_unit", f"{measurement!r} requires canonical unit {expected_unit!r}."))
        if field_identity in seen_fields:
            findings.append(MeasurementFinding("ERROR", "DUPLICATE_MEASUREMENT", row_number, "measurement", f"Duplicate measurement {measurement!r} in {key}/{mode_id}."))
            continue
        seen_fields.add(field_identity)
        raw_modes[identity]["fields"].append(MeasurementField(measurement, requirement, unit))

    seen_derived: set[tuple[str, str, str]] = set()
    for row_number, row in enumerate(derived_rows, start=2):
        key = row["catalogue_key"].strip()
        mode_id = row["mode_id"].strip()
        metric = row["derived_metric"].strip()
        identity = (key, mode_id)
        derived_identity = (key, mode_id, metric)
        if identity not in raw_modes:
            findings.append(MeasurementFinding("ERROR", "UNKNOWN_MODE_REFERENCE", row_number, "mode_id", f"Derived metric references unknown mode {key}/{mode_id}."))
            continue
        if metric not in derived_slugs:
            findings.append(MeasurementFinding("ERROR", "UNKNOWN_DERIVED_METRIC", row_number, "derived_metric", f"Unknown derived metric: {metric!r}."))
        if derived_identity in seen_derived:
            findings.append(MeasurementFinding("ERROR", "DUPLICATE_DERIVED_METRIC", row_number, "derived_metric", f"Duplicate derived metric {metric!r}."))
            continue
        seen_derived.add(derived_identity)
        raw_modes[identity]["derived"].append(metric)

    for key, identities in modes_by_exercise.items():
        defaults = sum(bool(raw_modes[identity]["is_default"]) for identity in identities)
        if defaults == 0:
            findings.append(MeasurementFinding("ERROR", "NO_DEFAULT_MODE", None, "is_default", f"{key!r} has no default measurement mode."))
        elif defaults > 1:
            findings.append(MeasurementFinding("ERROR", "MULTIPLE_DEFAULT_MODES", None, "is_default", f"{key!r} has multiple default measurement modes."))

    if required_catalogue_keys is not None:
        for missing in sorted(required_catalogue_keys - modes_by_exercise.keys()):
            findings.append(MeasurementFinding("ERROR", "MISSING_MEASUREMENT_MODES", None, "catalogue_key", f"Release-selected exercise {missing!r} has no measurement modes."))

    built: defaultdict[str, list[MeasurementMode]] = defaultdict(list)
    for identity, raw in raw_modes.items():
        fields = tuple(sorted(raw["fields"], key=lambda item: (item.requirement, item.measurement)))
        measurements = {field.measurement for field in fields}
        if not fields:
            findings.append(MeasurementFinding("ERROR", "EMPTY_MEASUREMENT_MODE", int(raw["row"]), "mode_id", f"{identity[0]}/{identity[1]} has no required or optional measurements."))

        load_semantics = str(raw["load_semantics"])
        if load_semantics in {"external_load", "added_load"} and "load" not in measurements:
            findings.append(MeasurementFinding("ERROR", "LOAD_SEMANTICS_WITHOUT_LOAD", int(raw["row"]), "load_semantics", f"{load_semantics} requires the load measurement."))
        if load_semantics == "assistance" and "assistance" not in measurements:
            findings.append(MeasurementFinding("ERROR", "ASSISTANCE_WITHOUT_MEASUREMENT", int(raw["row"]), "load_semantics", "Assistance semantics requires the assistance measurement."))
        if load_semantics == "assistance" and "load" in measurements:
            findings.append(MeasurementFinding("ERROR", "CONTRADICTORY_LOAD_SEMANTICS", int(raw["row"]), "load_semantics", "Assistance mode cannot also record added/external load."))
        if load_semantics in {"none", "bodyweight"} and measurements & {"load", "assistance"}:
            findings.append(MeasurementFinding("ERROR", "CONTRADICTORY_LOAD_SEMANTICS", int(raw["row"]), "load_semantics", f"{load_semantics} cannot record load or assistance."))

        derived_metrics = tuple(sorted(raw["derived"]))
        for metric in derived_metrics:
            required = DERIVATION_REQUIREMENTS.get(metric)
            if required and not required.issubset(measurements):
                findings.append(MeasurementFinding("ERROR", "INVALID_DERIVED_METRIC", int(raw["row"]), "derived_metric", f"{metric} requires {', '.join(sorted(required))}."))

        built[str(raw["catalogue_key"])].append(
            MeasurementMode(
                catalogue_key=str(raw["catalogue_key"]),
                mode_id=str(raw["mode_id"]),
                is_default=bool(raw["is_default"]),
                load_semantics=load_semantics,
                min_runtime_contract_version=int(raw["min_runtime_contract_version"]),
                fields=fields,
                derived_metrics=derived_metrics,
            )
        )

    ordered = {
        key: tuple(sorted(modes, key=lambda mode: mode.mode_id))
        for key, modes in sorted(built.items())
    }
    findings.sort(key=lambda item: (item.severity != "ERROR", item.row or 0, item.code, item.field))
    return findings, ordered


def runtime_modes(modes: tuple[MeasurementMode, ...]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for mode in modes:
        required = [field for field in mode.fields if field.requirement == "required"]
        optional = [field for field in mode.fields if field.requirement == "optional"]
        result.append(
            {
                "mode_id": mode.mode_id,
                "is_default": mode.is_default,
                "required": [
                    {"measurement": field.measurement, "unit": field.canonical_unit}
                    for field in sorted(required, key=lambda item: item.measurement)
                ],
                "optional": [
                    {"measurement": field.measurement, "unit": field.canonical_unit}
                    for field in sorted(optional, key=lambda item: item.measurement)
                ],
                "load_semantics": mode.load_semantics,
                "derived_metrics": list(mode.derived_metrics),
                "measurement_schema_version": MEASUREMENT_SCHEMA_VERSION,
            }
        )
    return result
