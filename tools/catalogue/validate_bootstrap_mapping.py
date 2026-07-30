#!/usr/bin/env python3
"""Validate the governed Human Strength legacy-seed identity bridge."""

from __future__ import annotations

import argparse
import csv
import json
from dataclasses import dataclass
from pathlib import Path

EXPECTED_LEGACY_SEEDS = {
    "bench_press": "Bench Press",
    "incline_db_press": "Incline Dumbbell Press",
    "chest_fly": "Chest Fly",
    "deadlift": "Deadlift",
    "pull_up": "Pull Up",
    "barbell_row": "Barbell Row",
    "lat_pulldown": "Lat Pulldown",
    "squat": "Barbell Squat",
    "romanian_deadlift": "Romanian Deadlift",
    "leg_press": "Leg Press",
    "calf_raise": "Calf Raise",
    "overhead_press": "Overhead Press",
    "lateral_raise": "Lateral Raise",
    "rear_delt_fly": "Rear Delt Fly",
    "bicep_curl": "Bicep Curl",
    "tricep_pushdown": "Tricep Pushdown",
    "hammer_curl": "Hammer Curl",
    "skull_crusher": "Skull Crusher",
    "hanging_leg_raise": "Hanging Leg Raise",
    "plank": "Plank",
    "crunch": "Abdominal Crunch",
}

RELATIONSHIPS = {
    "EXACT_IDENTITY",
    "NAMING_EQUIVALENT",
    "EQUIPMENT_SPECIFIC_EQUIVALENT",
    "POSSIBLE_MATCH_REQUIRES_REVIEW",
    "MISSING",
    "AMBIGUOUS_LEGACY_IDENTITY",
}

MAPPING_STATUSES = {
    "APPROVED",
    "CANONICAL_ALLOCATED_REVIEW_REQUIRED",
    "READY_FOR_CANONICAL_ALLOCATION",
    "NEEDS_HUMAN_REVIEW",
    "BLOCKED_BY_IDENTITY_AMBIGUITY",
    "BLOCKED_BY_DATA_QUALITY",
}

REQUIRED_ENTRY_FIELDS = {
    "legacy_seed_id",
    "legacy_display_name",
    "candidate_catalogue_key",
    "catalogue_display_name",
    "relationship",
    "review_status",
    "canonical_id",
    "mapping_status",
    "notes",
    "review_evidence",
}


@dataclass(frozen=True)
class MappingFinding:
    code: str
    entry: str
    message: str


def _manifest(path: Path) -> dict[str, dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return {
            row["catalogue_key"]: row
            for row in csv.DictReader(handle)
        }


def _canonical_ledger(path: Path) -> dict[str, str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return {
        entry["catalogue_key"]: entry["canonical_id"]
        for entry in data["entries"]
    }


def _retired_keys(path: Path) -> set[str]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return {
            row["value"].strip()
            for row in csv.DictReader(handle)
            if row["status"].strip() in {"Deprecated", "Retired"}
        }


def validate_bootstrap_mapping(
    mapping_path: Path,
    manifest_path: Path,
    canonical_ledger_path: Path,
    retired_keys_path: Path,
) -> list[MappingFinding]:
    findings: list[MappingFinding] = []
    try:
        data = json.loads(mapping_path.read_text(encoding="utf-8"))
        manifest = _manifest(manifest_path)
        ledger = _canonical_ledger(canonical_ledger_path)
        retired = _retired_keys(retired_keys_path)
    except (OSError, csv.Error, json.JSONDecodeError, KeyError) as exc:
        return [MappingFinding("MAPPING_INPUT", "", str(exc))]

    if data.get("mapping_version") != 1:
        findings.append(MappingFinding("MAPPING_VERSION", "", "mapping_version must equal 1."))
    if data.get("source_system") != "human_strength_room_seed":
        findings.append(MappingFinding("SOURCE_SYSTEM", "", "Unexpected source_system."))
    entries = data.get("entries")
    if not isinstance(entries, list):
        return findings + [MappingFinding("ENTRIES", "", "entries must be an array.")]
    if data.get("legacy_seed_count") != len(EXPECTED_LEGACY_SEEDS):
        findings.append(MappingFinding("DECLARED_COUNT", "", "legacy_seed_count must equal 21."))

    seen_legacy: set[str] = set()
    seen_canonical: dict[str, str] = {}
    for entry in entries:
        if not isinstance(entry, dict):
            findings.append(MappingFinding("ENTRY_TYPE", "", "Each entry must be an object."))
            continue
        legacy_id = str(entry.get("legacy_seed_id") or "")
        missing_fields = REQUIRED_ENTRY_FIELDS - set(entry)
        if missing_fields:
            findings.append(MappingFinding("ENTRY_FIELDS", legacy_id, f"Missing fields: {sorted(missing_fields)}"))
        if not legacy_id:
            findings.append(MappingFinding("LEGACY_ID", "", "legacy_seed_id is required."))
            continue
        if legacy_id in seen_legacy:
            findings.append(MappingFinding("DUPLICATE_LEGACY_ID", legacy_id, "Legacy seed appears more than once."))
        seen_legacy.add(legacy_id)

        expected_name = EXPECTED_LEGACY_SEEDS.get(legacy_id)
        if expected_name is None:
            findings.append(MappingFinding("UNKNOWN_LEGACY_ID", legacy_id, "Legacy seed ID is not in the governed set."))
        elif entry.get("legacy_display_name") != expected_name:
            findings.append(MappingFinding("LEGACY_NAME_EVIDENCE", legacy_id, "Display name does not match the audited seed evidence."))

        relationship = entry.get("relationship")
        status = entry.get("mapping_status")
        if relationship not in RELATIONSHIPS:
            findings.append(MappingFinding("RELATIONSHIP", legacy_id, "Unknown relationship classification."))
        if status not in MAPPING_STATUSES:
            findings.append(MappingFinding("MAPPING_STATUS", legacy_id, "Unknown mapping status."))
        for field in ("review_status", "notes", "review_evidence"):
            if not str(entry.get(field) or "").strip():
                findings.append(MappingFinding("REVIEW_EVIDENCE", legacy_id, f"{field} must be explicit."))

        key = entry.get("candidate_catalogue_key")
        display = entry.get("catalogue_display_name")
        canonical_id = entry.get("canonical_id")
        if key is None:
            if relationship not in {"MISSING", "AMBIGUOUS_LEGACY_IDENTITY"}:
                findings.append(MappingFinding("UNRESOLVED_MAPPING", legacy_id, "Null candidate must be explicitly missing or ambiguous."))
            if display is not None or canonical_id is not None:
                findings.append(MappingFinding("UNRESOLVED_PAYLOAD", legacy_id, "Unresolved mapping cannot carry display or canonical identity."))
        elif key not in manifest:
            findings.append(MappingFinding("UNKNOWN_CANDIDATE", legacy_id, f"Unknown catalogue key: {key!r}."))
        else:
            record = manifest[key]
            if display != record["canonical_name"]:
                findings.append(MappingFinding("CATALOGUE_NAME_EVIDENCE", legacy_id, "Catalogue display evidence does not match the candidate record."))
            expected_canonical = ledger.get(key)
            if canonical_id != expected_canonical:
                findings.append(MappingFinding("CANONICAL_LEDGER_MISMATCH", legacy_id, "Canonical ID must match the append-only identity ledger."))
            if canonical_id:
                previous = seen_canonical.get(canonical_id)
                if previous and previous != legacy_id:
                    findings.append(MappingFinding("CANONICAL_ID_REUSE", legacy_id, f"Canonical ID is already mapped from {previous!r}."))
                seen_canonical[canonical_id] = legacy_id
            if status == "APPROVED":
                if canonical_id is None:
                    findings.append(MappingFinding("APPROVED_WITHOUT_CANONICAL_ID", legacy_id, "Approved mapping requires a canonical ID."))
                if record["review_status"] != "Approved":
                    findings.append(MappingFinding("APPROVED_DRAFT", legacy_id, "Approved mapping cannot target an unapproved record."))
                if key in retired:
                    findings.append(MappingFinding("APPROVED_RETIRED", legacy_id, "Approved mapping cannot target a retired or deprecated key."))

    missing = set(EXPECTED_LEGACY_SEEDS) - seen_legacy
    extra = seen_legacy - set(EXPECTED_LEGACY_SEEDS)
    if missing:
        findings.append(MappingFinding("MISSING_LEGACY_SEEDS", "", f"Missing legacy IDs: {sorted(missing)}"))
    if extra:
        findings.append(MappingFinding("EXTRA_LEGACY_SEEDS", "", f"Unexpected legacy IDs: {sorted(extra)}"))
    if len(entries) != len(EXPECTED_LEGACY_SEEDS):
        findings.append(MappingFinding("ENTRY_COUNT", "", f"Expected 21 entries, found {len(entries)}."))
    return sorted(findings, key=lambda finding: (finding.entry, finding.code, finding.message))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mapping", type=Path, default=Path("catalogue/mapping/strength-bootstrap-mapping-v1.json"))
    parser.add_argument("--manifest", type=Path, default=Path("catalogue/candidate-manifest-v2.csv"))
    parser.add_argument("--canonical-ledger", type=Path, default=Path("catalogue/runtime/canonical-id-map-v1.json"))
    parser.add_argument("--retired-keys", type=Path, default=Path("catalogue/reference/v2/retired-keys.csv"))
    args = parser.parse_args()
    findings = validate_bootstrap_mapping(args.mapping, args.manifest, args.canonical_ledger, args.retired_keys)
    for finding in findings:
        print(f"ERROR {finding.code} {finding.entry}: {finding.message}")
    if findings:
        print(f"Bootstrap mapping failed with {len(findings)} error(s).")
        return 1
    print("Validated bootstrap mapping: 21 legacy seeds, 0 errors.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
