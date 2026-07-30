#!/usr/bin/env python3
"""Build a deterministic runtime catalogue release from validated Schema v2 data."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

from validate_catalogue import split_values, validate_v2
from measurement_semantics import (
    RUNTIME_CONTRACT_VERSION,
    runtime_modes,
    validate_measurement_semantics,
)

MAPPING_CONTRACT_VERSION = 1
PILOT_KEYS = (
    "push_up",
    "barbell_bench_press",
    "goblet_squat",
    "seated_cable_row",
    "selectorized_chest_press",
    "bulgarian_split_squat",
    "plank",
    "farmers_carry",
)
CHANNELS = ("pilot_staging", "production")
IDENTITY_SOURCES = {"legacy_seed", "new_allocation"}
RELATIONSHIP_FIELDS = {
    "parent_exercise_key": "parent",
    "progression_keys": "progression",
    "regression_keys": "regression",
    "supersedes_key": "supersedes",
}
REFERENCE_FIELDS = {
    "exercise_family": "exercise-families.csv",
    "primary_movement_pattern": "movement-patterns.csv",
    "difficulty": "difficulty.csv",
    "laterality": "laterality.csv",
    "compound_or_isolation": "compound-isolation.csv",
    "primary_muscles": "muscles.csv",
    "secondary_muscles": "muscles.csv",
    "stabiliser_muscles": "muscles.csv",
    "equipment": "equipment.csv",
    "attachment_or_implement": "attachments.csv",
}
ALIAS_FIELDS = {
    "search_aliases": "search",
    "regional_aliases": "regional",
    "abbreviations": "abbreviation",
    "legacy_names": "legacy",
}
REQUIRED_RUNTIME_EXERCISE_FIELDS = {
    "canonical_id",
    "display_name",
    "classification",
    "search",
    "anatomy",
    "equipment",
    "coaching",
    "relationships",
    "measurement_modes",
}


class RuntimeBuildError(ValueError):
    """Raised when trusted runtime output cannot be produced."""


def canonical_json(value: Any) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def checksum_for(payload: dict[str, Any]) -> str:
    return hashlib.sha256(canonical_json(payload)).hexdigest()


def normalise_search(value: str) -> str:
    return " ".join(re.findall(r"[a-z0-9]+", value.casefold()))


def read_csv_rows(path: Path) -> list[dict[str, str]]:
    try:
        with path.open(newline="", encoding="utf-8-sig") as handle:
            return list(csv.DictReader(handle))
    except (OSError, csv.Error) as exc:
        raise RuntimeBuildError(f"Could not read CSV {path}: {exc}") from exc


def load_reference_slugs(references: Path) -> dict[str, dict[str, str]]:
    lookups: dict[str, dict[str, str]] = {}
    for filename in sorted(set(REFERENCE_FIELDS.values())):
        rows = read_csv_rows(references / filename)
        lookup = {
            row["value"].strip(): row["slug"].strip()
            for row in rows
            if row.get("status") in {"Active", "Caution", "Deprecated"}
        }
        if not lookup:
            raise RuntimeBuildError(f"Reference file has no usable values: {filename}")
        lookups[filename] = lookup
    return lookups


def load_mapping(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RuntimeBuildError(f"Could not read canonical ID mapping {path}: {exc}") from exc

    if data.get("runtime_contract_version") != MAPPING_CONTRACT_VERSION:
        raise RuntimeBuildError(
            f"Unsupported runtime contract version in mapping: "
            f"{data.get('runtime_contract_version')!r}"
        )
    if not isinstance(data.get("catalogue_version"), str) or not data["catalogue_version"]:
        raise RuntimeBuildError("Mapping requires a non-empty catalogue_version.")
    if not isinstance(data.get("source_catalogue_commit"), str) or not data["source_catalogue_commit"]:
        raise RuntimeBuildError("Mapping requires source_catalogue_commit.")
    entries = data.get("entries")
    if not isinstance(entries, list):
        raise RuntimeBuildError("Mapping entries must be a list.")

    keys: set[str] = set()
    canonical_ids: set[str] = set()
    mapped: dict[str, dict[str, str]] = {}
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise RuntimeBuildError(f"Mapping entry {index} must be an object.")
        key = entry.get("catalogue_key")
        canonical_id = entry.get("canonical_id")
        identity_source = entry.get("identity_source")
        if not isinstance(key, str) or not re.fullmatch(r"[a-z0-9_]+", key):
            raise RuntimeBuildError(f"Invalid catalogue_key in mapping entry {index}: {key!r}")
        if not isinstance(canonical_id, str) or not re.fullmatch(r"[a-z0-9_]+", canonical_id):
            raise RuntimeBuildError(
                f"Invalid canonical_id in mapping entry {index}: {canonical_id!r}"
            )
        if identity_source not in IDENTITY_SOURCES:
            raise RuntimeBuildError(
                f"Invalid identity_source in mapping entry {index}: {identity_source!r}"
            )
        if key in keys:
            raise RuntimeBuildError(f"Duplicate catalogue_key in mapping: {key}")
        if canonical_id in canonical_ids:
            raise RuntimeBuildError(f"Duplicate canonical_id in mapping: {canonical_id}")
        keys.add(key)
        canonical_ids.add(canonical_id)
        mapped[key] = {
            "canonical_id": canonical_id,
            "identity_source": identity_source,
        }

    missing = sorted(set(PILOT_KEYS) - keys)
    extra = sorted(keys - set(PILOT_KEYS))
    if missing:
        raise RuntimeBuildError(f"Missing canonical ID mapping(s): {', '.join(missing)}")
    if extra:
        raise RuntimeBuildError(f"Unexpected non-pilot mapping(s): {', '.join(extra)}")
    data["by_key"] = mapped
    return data


def require_slug(
    value: str,
    field: str,
    lookups: dict[str, dict[str, str]],
) -> str:
    filename = REFERENCE_FIELDS[field]
    try:
        return lookups[filename][value]
    except KeyError as exc:
        raise RuntimeBuildError(f"Unsupported {field} value: {value!r}") from exc


def slugs_for(
    value: str,
    field: str,
    lookups: dict[str, dict[str, str]],
) -> list[str]:
    return [require_slug(item, field, lookups) for item in split_values(value)]


def production_eligible(row: dict[str, str]) -> bool:
    return (
        row.get("review_status") == "Approved"
        and row.get("content_origin") not in {
            "AI Generated — Review Pending",
            "Imported — Review Pending",
        }
        and row.get("coaching_review_status") in {"Completed", "Not Required"}
        and row.get("clinical_review_status") in {"Completed", "Not Required"}
        and not split_values(row.get("ai_review_flags") or "")
    )


def build_aliases(row: dict[str, str]) -> list[dict[str, str]]:
    aliases: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for field, alias_type in ALIAS_FIELDS.items():
        for value in split_values(row.get(field) or ""):
            item = (alias_type, normalise_search(value))
            if not item[1] or item in seen:
                continue
            seen.add(item)
            aliases.append(
                {
                    "value": value,
                    "normalised": item[1],
                    "type": alias_type,
                }
            )
    return sorted(aliases, key=lambda item: (item["type"], item["normalised"], item["value"]))


def resolve_relationships(
    row: dict[str, str],
    mapping: dict[str, dict[str, str]],
    all_source_keys: set[str],
) -> list[dict[str, str]]:
    relationships: list[dict[str, str]] = []
    for field, relationship_type in RELATIONSHIP_FIELDS.items():
        for target_key in split_values(row.get(field) or ""):
            if target_key not in all_source_keys:
                raise RuntimeBuildError(
                    f"{row['catalogue_key']} has unresolved {relationship_type} target: "
                    f"{target_key}"
                )
            target = mapping.get(target_key)
            if target is None:
                # The isolated Pilot fixture omits valid relationships to records outside
                # the selected subset. A full release requires those targets in its map.
                continue
            relationships.append(
                {
                    "type": relationship_type,
                    "target_canonical_id": target["canonical_id"],
                }
            )
    return sorted(
        relationships,
        key=lambda item: (item["type"], item["target_canonical_id"]),
    )


def transform_exercise(
    row: dict[str, str],
    identity: dict[str, str],
    mapping: dict[str, dict[str, str]],
    all_source_keys: set[str],
    lookups: dict[str, dict[str, str]],
    measurement_modes_by_key: dict[str, tuple],
) -> dict[str, Any]:
    aliases = build_aliases(row)
    keywords = sorted(
        {
            normalise_search(value)
            for value in split_values(row.get("search_keywords") or "")
            if normalise_search(value)
        }
    )
    exercise = {
        "canonical_id": identity["canonical_id"],
        "display_name": row["canonical_name"],
        "classification": {
            "family": require_slug(row["exercise_family"], "exercise_family", lookups),
            "movement_pattern": require_slug(
                row["primary_movement_pattern"],
                "primary_movement_pattern",
                lookups,
            ),
            "difficulty": require_slug(row["difficulty"], "difficulty", lookups),
            "laterality": require_slug(row["laterality"], "laterality", lookups),
            "compound_or_isolation": require_slug(
                row["compound_or_isolation"],
                "compound_or_isolation",
                lookups,
            ),
        },
        "search": {
            "aliases": aliases,
            "keywords": keywords,
        },
        "anatomy": {
            "primary_muscles": slugs_for(
                row["primary_muscles"], "primary_muscles", lookups
            ),
            "secondary_muscles": slugs_for(
                row.get("secondary_muscles") or "", "secondary_muscles", lookups
            ),
            "stabiliser_muscles": slugs_for(
                row.get("stabiliser_muscles") or "",
                "stabiliser_muscles",
                lookups,
            ),
        },
        "equipment": {
            "required": slugs_for(row["equipment"], "equipment", lookups),
            "attachments": slugs_for(
                row.get("attachment_or_implement") or "",
                "attachment_or_implement",
                lookups,
            ),
        },
        "coaching": {
            "setup": row.get("setup_cues") or "",
            "execution": row.get("execution_cues") or "",
            "common_errors": row.get("common_errors") or "",
            "breathing_bracing": row.get("breathing_bracing_notes") or "",
            "range_of_motion": row.get("range_of_motion_notes") or "",
            "safety": row.get("safety_notes") or "",
        },
        "relationships": resolve_relationships(
            row,
            mapping,
            all_source_keys,
        ),
        "measurement_modes": runtime_modes(
            measurement_modes_by_key[row["catalogue_key"]]
        ),
    }
    validate_runtime_exercise(exercise)
    return exercise


def validate_runtime_exercise(exercise: dict[str, Any]) -> None:
    missing = sorted(REQUIRED_RUNTIME_EXERCISE_FIELDS - exercise.keys())
    if missing:
        raise RuntimeBuildError(
            f"Runtime exercise is missing required fields: {', '.join(missing)}"
        )
    if not exercise["canonical_id"] or not exercise["display_name"]:
        raise RuntimeBuildError("Runtime exercise identity cannot be empty.")
    if not exercise["anatomy"]["primary_muscles"]:
        raise RuntimeBuildError(
            f"{exercise['canonical_id']} requires at least one primary muscle."
        )
    if not exercise["equipment"]["required"]:
        raise RuntimeBuildError(
            f"{exercise['canonical_id']} requires at least one equipment value."
        )
    modes = exercise["measurement_modes"]
    if not modes or sum(bool(mode["is_default"]) for mode in modes) != 1:
        raise RuntimeBuildError(
            f"{exercise['canonical_id']} requires exactly one default measurement mode."
        )


def build_release(
    manifest: Path,
    references: Path,
    mapping_path: Path,
    channel: str,
    measurement_directory: Path | None = None,
) -> dict[str, Any]:
    if channel not in CHANNELS:
        raise RuntimeBuildError(f"Unsupported release channel: {channel!r}")

    findings, row_count = validate_v2(manifest, references)
    if findings:
        summary = "; ".join(
            f"{finding.code} row={finding.row} field={finding.field}"
            for finding in findings[:5]
        )
        raise RuntimeBuildError(f"Schema v2 validation failed: {summary}")
    if row_count != 48:
        raise RuntimeBuildError(f"Expected 48 Schema v2 rows, found {row_count}.")

    rows = read_csv_rows(manifest)
    rows_by_key = {row.get("catalogue_key", ""): row for row in rows}
    if len(rows_by_key) != len(rows):
        raise RuntimeBuildError("Source manifest contains duplicate catalogue keys.")
    missing_source = sorted(set(PILOT_KEYS) - rows_by_key.keys())
    if missing_source:
        raise RuntimeBuildError(f"Pilot source record(s) missing: {', '.join(missing_source)}")

    measurement_directory = (
        measurement_directory
        or Path(__file__).resolve().parents[2] / "catalogue" / "measurement"
    )
    measurement_findings, measurement_modes_by_key = validate_measurement_semantics(
        set(rows_by_key),
        references,
        measurement_directory / "measurement-modes-v1.csv",
        measurement_directory / "measurement-mode-fields-v1.csv",
        measurement_directory / "measurement-mode-derived-v1.csv",
        required_catalogue_keys=set(PILOT_KEYS),
    )
    if measurement_findings:
        summary = "; ".join(
            f"{finding.code} row={finding.row} field={finding.field}"
            for finding in measurement_findings[:5]
        )
        raise RuntimeBuildError(f"Measurement semantics validation failed: {summary}")

    mapping_data = load_mapping(mapping_path)
    mapping = mapping_data["by_key"]
    lookups = load_reference_slugs(references)

    selected_rows = [rows_by_key[key] for key in PILOT_KEYS]
    if channel == "production":
        ineligible = [
            row["catalogue_key"]
            for row in selected_rows
            if not production_eligible(row)
        ]
        if ineligible:
            raise RuntimeBuildError(
                "Production channel rejected ineligible record(s): "
                + ", ".join(sorted(ineligible))
            )

    exercises = [
        transform_exercise(
            row,
            mapping[row["catalogue_key"]],
            mapping,
            set(rows_by_key),
            lookups,
            measurement_modes_by_key,
        )
        for row in selected_rows
    ]
    exercises.sort(key=lambda item: item["canonical_id"])

    payload = {
        "runtime_contract_version": RUNTIME_CONTRACT_VERSION,
        "schema_version": "2.0",
        "catalogue_version": mapping_data["catalogue_version"],
        "source_catalogue_commit": mapping_data["source_catalogue_commit"],
        "channel": channel,
        "distribution_scope": "official_catalogue_release",
        "record_count": len(exercises),
        "exercises": exercises,
    }
    return {**payload, "checksum": checksum_for(payload)}


def serialise_release(release: dict[str, Any]) -> bytes:
    return (
        json.dumps(release, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    ).encode("utf-8")


def main(argv: list[str] | None = None) -> int:
    workspace = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--channel", choices=CHANNELS, required=True)
    parser.add_argument(
        "--manifest",
        type=Path,
        default=workspace / "catalogue" / "candidate-manifest-v2.csv",
    )
    parser.add_argument(
        "--references",
        type=Path,
        default=workspace / "catalogue" / "reference" / "v2",
    )
    parser.add_argument(
        "--mapping",
        type=Path,
        default=workspace / "catalogue" / "runtime" / "canonical-id-map-v1.json",
    )
    parser.add_argument(
        "--measurement-directory",
        type=Path,
        default=workspace / "catalogue" / "measurement",
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Verify that output already equals the deterministic build.",
    )
    args = parser.parse_args(argv)

    try:
        release = build_release(
            args.manifest,
            args.references,
            args.mapping,
            args.channel,
            args.measurement_directory,
        )
        output = serialise_release(release)
        if args.check:
            try:
                existing = args.output.read_bytes()
            except OSError as exc:
                raise RuntimeBuildError(
                    f"Could not read fixture for comparison: {args.output}: {exc}"
                ) from exc
            if existing != output:
                raise RuntimeBuildError(
                    f"Fixture is not reproducible: {args.output}"
                )
        else:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_bytes(output)
    except RuntimeBuildError as exc:
        print(f"Runtime catalogue build failed: {exc}", file=sys.stderr)
        return 1

    action = "Verified" if args.check else "Generated"
    print(
        f"{action} runtime catalogue: records={release['record_count']} "
        f"contract={release['runtime_contract_version']} "
        f"channel={release['channel']} checksum={release['checksum']}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
