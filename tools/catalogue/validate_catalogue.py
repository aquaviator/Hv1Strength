#!/usr/bin/env python3
"""Validate an explicitly selected Human Strength catalogue schema."""

from __future__ import annotations

import argparse
import csv
import difflib
import json
import os
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

V1_REQUIRED_COLUMNS = [
    "catalogue_key", "exercise_family", "parent_exercise", "canonical_name",
    "variation_type", "equipment", "primary_movement_pattern",
    "secondary_movement_pattern", "primary_muscle_group",
    "secondary_muscle_groups", "difficulty", "technical_complexity",
    "facility_tier", "commercial_importance", "launch_priority",
    "search_aliases", "manufacturer_aliases", "regional_aliases",
    "future_module_tags", "substitution_group", "review_status", "review_notes",
]
V1_REQUIRED_VALUES = [
    "catalogue_key", "exercise_family", "parent_exercise", "canonical_name",
    "variation_type", "equipment", "primary_movement_pattern",
    "primary_muscle_group", "secondary_muscle_groups", "difficulty",
    "technical_complexity", "facility_tier", "commercial_importance",
    "launch_priority", "review_status", "review_notes",
]
V1_CONTROLLED_SINGLE = {
    "exercise_family": "exercise_family",
    "variation_type": "variation_type",
    "primary_movement_pattern": "movement_pattern",
    "secondary_movement_pattern": "movement_pattern",
    "primary_muscle_group": "muscle_group",
    "difficulty": "difficulty",
    "technical_complexity": "technical_complexity",
    "facility_tier": "facility_tier",
    "commercial_importance": "commercial_importance",
    "launch_priority": "launch_priority",
    "review_status": "review_status",
}
V1_CONTROLLED_MULTI = {
    "secondary_muscle_groups": "muscle_group",
    "future_module_tags": "future_module_tags",
}
V1_ALIAS_FIELDS = ("search_aliases", "manufacturer_aliases", "regional_aliases")
V1_MULTI_VALUE_FIELDS = ("equipment", *V1_CONTROLLED_MULTI, *V1_ALIAS_FIELDS)

V2_COLUMNS = [
    "schema_version", "catalogue_key", "canonical_name", "parent_exercise_key",
    "variation_type", "supersedes_key", "review_status", "review_notes",
    "content_origin", "exercise_family", "temporary_android_category",
    "laterality", "compound_or_isolation", "exercise_role", "difficulty",
    "technical_complexity", "facility_tier", "primary_movement_pattern",
    "secondary_movement_patterns", "primary_joint_actions",
    "secondary_joint_actions", "support_type", "torso_position",
    "loading_position", "grip_type", "bench_angle", "primary_muscles",
    "secondary_muscles", "stabiliser_muscles", "equipment",
    "attachment_or_implement", "external_load", "setup_cues", "execution_cues",
    "common_errors", "safety_notes", "range_of_motion_notes",
    "breathing_bracing_notes", "coaching_review_status",
    "clinical_review_status", "training_goals", "suitable_rep_styles",
    "loadability", "substitution_group", "progression_keys", "regression_keys",
    "contraindication_flags", "search_aliases", "manufacturer_aliases",
    "regional_aliases", "abbreviations", "legacy_names", "search_keywords",
    "ai_assistance_tasks", "ai_review_flags", "ai_suitability_tags",
    "source_provenance", "human_verified_fields",
]
V2_REQUIRED_VALUES = [
    "schema_version", "catalogue_key", "canonical_name", "variation_type",
    "review_status", "review_notes", "content_origin", "exercise_family",
    "laterality", "compound_or_isolation", "exercise_role", "difficulty",
    "technical_complexity", "facility_tier", "primary_movement_pattern",
    "primary_joint_actions", "support_type", "torso_position",
    "loading_position", "bench_angle", "primary_muscles", "equipment",
    "external_load", "coaching_review_status", "clinical_review_status",
    "training_goals", "loadability",
]
V2_REFERENCE_FILES = {
    "primary_movement_pattern": "movement-patterns.csv",
    "secondary_movement_patterns": "movement-patterns.csv",
    "primary_joint_actions": "joint-actions.csv",
    "secondary_joint_actions": "joint-actions.csv",
    "primary_muscles": "muscles.csv",
    "secondary_muscles": "muscles.csv",
    "stabiliser_muscles": "muscles.csv",
    "equipment": "equipment.csv",
    "attachment_or_implement": "attachments.csv",
    "exercise_family": "exercise-families.csv",
    "temporary_android_category": "android-categories.csv",
    "laterality": "laterality.csv",
    "compound_or_isolation": "compound-isolation.csv",
    "exercise_role": "exercise-roles.csv",
    "difficulty": "difficulty.csv",
    "technical_complexity": "technical-complexity.csv",
    "facility_tier": "facility-tiers.csv",
    "support_type": "support-types.csv",
    "torso_position": "torso-positions.csv",
    "loading_position": "loading-positions.csv",
    "grip_type": "grip-types.csv",
    "bench_angle": "bench-angles.csv",
    "training_goals": "training-goals.csv",
    "suitable_rep_styles": "suitable-rep-styles.csv",
    "loadability": "loadability.csv",
    "external_load": "external-load.csv",
    "review_status": "review-statuses.csv",
    "coaching_review_status": "coaching-review-statuses.csv",
    "clinical_review_status": "clinical-review-statuses.csv",
    "content_origin": "content-origins.csv",
    "ai_assistance_tasks": "ai-assistance-tasks.csv",
    "ai_review_flags": "ai-review-flags.csv",
    "ai_suitability_tags": "ai-suitability-tags.csv",
    "variation_type": "variation-types.csv",
    "contraindication_flags": "contraindication-flags.csv",
}
V2_MULTI_CONTROLLED_FIELDS = {
    "secondary_movement_patterns", "primary_joint_actions",
    "secondary_joint_actions", "primary_muscles", "secondary_muscles",
    "stabiliser_muscles", "equipment", "attachment_or_implement",
    "exercise_role", "loading_position", "grip_type", "training_goals",
    "suitable_rep_styles", "contraindication_flags", "ai_assistance_tasks",
    "ai_review_flags", "ai_suitability_tags",
}
V2_ALIAS_FIELDS = (
    "search_aliases", "manufacturer_aliases", "regional_aliases",
    "abbreviations", "legacy_names",
)
V2_KEY_REFERENCE_FIELDS = (
    "parent_exercise_key", "supersedes_key", "progression_keys", "regression_keys",
)
V2_MULTI_FREE_FIELDS = (
    *V2_ALIAS_FIELDS, "search_keywords", "progression_keys", "regression_keys",
    "human_verified_fields",
)
V2_MULTI_VALUE_FIELDS = tuple(sorted(V2_MULTI_CONTROLLED_FIELDS | set(V2_MULTI_FREE_FIELDS)))
REFERENCE_COLUMNS = ["value", "slug", "status", "description", "notes", "sort_order"]
V2_EXTRA_REFERENCE_FILES = ("retired-keys.csv",)
GRIPPED_EQUIPMENT = {
    "Barbell", "Dumbbell", "Kettlebell", "Pull-Up Bar", "Farmer Handles",
    "Trap Bar", "Landmine", "Strongman Log", "Safety Squat Bar",
}
AI_ORIGINS = {
    "Human Authored with AI Assistance", "AI Generated — Review Pending",
}
COACHING_FIELDS = (
    "setup_cues", "execution_cues", "common_errors", "safety_notes",
    "range_of_motion_notes", "breathing_bracing_notes",
)


@dataclass(frozen=True)
class Finding:
    severity: str
    code: str
    row: int | None
    field: str
    message: str


def split_values(value: str) -> list[str]:
    return [part.strip() for part in value.split("|") if part.strip()]


def has_empty_multi_value(value: str) -> bool:
    return "|" in value and any(not part.strip() for part in value.split("|"))


def norm(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", value.casefold()).strip()


def add(findings, severity, code, row, field, message):
    findings.append(Finding(severity, code, row, field, message))


def read_manifest(manifest: Path):
    with manifest.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        return reader.fieldnames or [], list(reader)


def load_v1_inputs(manifest: Path, references: Path):
    with (references / "controlled-values.json").open(encoding="utf-8") as handle:
        controlled = json.load(handle)
    with (references / "equipment.csv").open(newline="", encoding="utf-8-sig") as handle:
        equipment = {row["equipment"].strip() for row in csv.DictReader(handle)}
    columns, rows = read_manifest(manifest)
    return columns, rows, controlled, equipment


def validate_v1(manifest: Path, references: Path) -> tuple[list[Finding], int]:
    findings: list[Finding] = []
    try:
        columns, rows, controlled, equipment_values = load_v1_inputs(manifest, references)
    except (OSError, csv.Error, json.JSONDecodeError, KeyError) as exc:
        return [Finding("ERROR", "INPUT", None, "", str(exc))], 0

    missing = [column for column in V1_REQUIRED_COLUMNS if column not in columns]
    extra = [column for column in columns if column not in V1_REQUIRED_COLUMNS]
    for column in missing:
        add(findings, "ERROR", "MISSING_COLUMN", None, column, "Required CSV column is absent.")
    for column in extra:
        add(findings, "WARNING", "EXTRA_COLUMN", None, column, "Column is not defined by schema v1.")
    if missing:
        return findings, len(rows)

    names: defaultdict[str, list[tuple[int, str]]] = defaultdict(list)
    keys: defaultdict[str, list[int]] = defaultdict(list)
    aliases: list[tuple[int, str, str]] = []

    for index, row in enumerate(rows, start=2):
        for field in V1_REQUIRED_VALUES:
            if not (row.get(field) or "").strip():
                add(findings, "ERROR", "REQUIRED_VALUE", index, field, "Required value is blank.")

        key = (row.get("catalogue_key") or "").strip()
        if key:
            keys[key.casefold()].append(index)
            if not re.fullmatch(r"[a-z0-9]+(?:_[a-z0-9]+)*", key):
                add(findings, "ERROR", "KEY_FORMAT", index, "catalogue_key", "Use lowercase ASCII words separated by underscores.")

        name = (row.get("canonical_name") or "").strip()
        if name:
            names[norm(name)].append((index, name))

        for field in V1_MULTI_VALUE_FIELDS:
            value = row.get(field) or ""
            if has_empty_multi_value(value):
                add(findings, "ERROR", "EMPTY_MULTI_VALUE", index, field, "Pipe-separated values may not contain empty elements.")

        for field, vocabulary in V1_CONTROLLED_SINGLE.items():
            value = (row.get(field) or "").strip()
            if value and value not in controlled[vocabulary]:
                add(findings, "ERROR", "CONTROLLED_VALUE", index, field, f"Unknown value: {value!r}.")

        for field, vocabulary in V1_CONTROLLED_MULTI.items():
            values = split_values(row.get(field) or "")
            for value in values:
                if value not in controlled[vocabulary]:
                    add(findings, "ERROR", "CONTROLLED_VALUE", index, field, f"Unknown value: {value!r}.")
            if len(values) != len(set(values)):
                add(findings, "WARNING", "REPEATED_VALUE", index, field, "A multi-value field repeats a value.")

        secondary_pattern = (row.get("secondary_movement_pattern") or "").strip()
        if secondary_pattern and secondary_pattern == (row.get("primary_movement_pattern") or "").strip():
            add(findings, "WARNING", "REPEATED_PATTERN", index, "secondary_movement_pattern", "Secondary pattern duplicates primary pattern.")

        for value in split_values(row.get("equipment") or ""):
            if value not in equipment_values:
                add(findings, "ERROR", "UNKNOWN_EQUIPMENT", index, "equipment", f"Equipment is not in reference file: {value!r}.")

        for field in V1_ALIAS_FIELDS:
            field_aliases = split_values(row.get(field) or "")
            if len(field_aliases) != len({norm(alias) for alias in field_aliases}):
                add(findings, "WARNING", "REPEATED_ALIAS", index, field, "Alias is repeated in this field.")
            aliases.extend((index, field, alias) for alias in field_aliases)

    for key, line_numbers in keys.items():
        if len(line_numbers) > 1:
            for row_number in line_numbers:
                add(findings, "ERROR", "DUPLICATE_KEY", row_number, "catalogue_key", f"Duplicate key {key!r}.")
    for occurrences in names.values():
        if len(occurrences) > 1:
            for row_number, value in occurrences:
                add(findings, "ERROR", "DUPLICATE_NAME", row_number, "canonical_name", f"Duplicate canonical name {value!r}.")

    canonical_lookup = {key: values[0][1] for key, values in names.items()}
    for row_number, field, alias in aliases:
        if norm(alias) in canonical_lookup:
            add(findings, "ERROR", "ALIAS_IS_CANONICAL", row_number, field, f"Alias {alias!r} matches canonical name {canonical_lookup[norm(alias)]!r}.")

    unique_names = [(values[0][0], values[0][1]) for values in names.values()]
    for left_index in range(len(unique_names)):
        row_a, name_a = unique_names[left_index]
        for row_b, name_b in unique_names[left_index + 1:]:
            ratio = difflib.SequenceMatcher(None, norm(name_a), norm(name_b)).ratio()
            if ratio >= 0.86:
                add(findings, "WARNING", "NEAR_DUPLICATE", row_b, "canonical_name", f"{name_b!r} is {ratio:.0%} similar to row {row_a} {name_a!r}.")

    return sorted_findings(findings), len(rows)


def validate_reference_data(references: Path) -> tuple[list[Finding], dict[str, set[str]], dict[str, dict[str, str]]]:
    findings: list[Finding] = []
    values_by_file: dict[str, set[str]] = {}
    statuses_by_file: dict[str, dict[str, str]] = {}
    schema_path = references / "schema-version.json"
    try:
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        add(findings, "ERROR", "REFERENCE_SCHEMA", None, str(schema_path), str(exc))
        return findings, values_by_file, statuses_by_file
    if schema.get("schema_version") != "2.0":
        add(findings, "ERROR", "REFERENCE_SCHEMA_VERSION", None, "schema_version", "v2 reference schema must equal '2.0'.")
    allowed_statuses = set(schema.get("reference_statuses") or [])
    required_files = sorted(set(V2_REFERENCE_FILES.values()) | set(V2_EXTRA_REFERENCE_FILES))

    for filename in required_files:
        path = references / filename
        try:
            with path.open(newline="", encoding="utf-8-sig") as handle:
                reader = csv.DictReader(handle)
                columns = reader.fieldnames or []
                rows = list(reader)
        except (OSError, csv.Error) as exc:
            add(findings, "ERROR", "MISSING_REFERENCE", None, filename, str(exc))
            continue
        if columns != REFERENCE_COLUMNS:
            add(findings, "ERROR", "REFERENCE_COLUMNS", None, filename, f"Expected columns {REFERENCE_COLUMNS!r}.")
            continue

        seen_values: dict[str, int] = {}
        seen_slugs: dict[str, int] = {}
        seen_orders: dict[str, int] = {}
        values: set[str] = set()
        statuses: dict[str, str] = {}
        for row_number, row in enumerate(rows, start=2):
            value = (row.get("value") or "").strip()
            slug = (row.get("slug") or "").strip()
            status = (row.get("status") or "").strip()
            description = (row.get("description") or "").strip()
            order = (row.get("sort_order") or "").strip()
            for field, item in (("value", value), ("slug", slug), ("status", status), ("description", description), ("sort_order", order)):
                if not item:
                    add(findings, "ERROR", "REFERENCE_REQUIRED_VALUE", row_number, f"{filename}:{field}", "Reference value is blank.")
            value_key = value.casefold()
            slug_key = slug.casefold()
            if value_key in seen_values:
                add(findings, "ERROR", "DUPLICATE_REFERENCE_VALUE", row_number, filename, f"{value!r} duplicates row {seen_values[value_key]}.")
            else:
                seen_values[value_key] = row_number
            if slug_key in seen_slugs:
                add(findings, "ERROR", "DUPLICATE_REFERENCE_SLUG", row_number, filename, f"{slug!r} duplicates row {seen_slugs[slug_key]}.")
            else:
                seen_slugs[slug_key] = row_number
            if slug and not re.fullmatch(r"[a-z0-9]+(?:_[a-z0-9]+)*", slug):
                add(findings, "ERROR", "REFERENCE_SLUG_FORMAT", row_number, filename, f"Invalid slug: {slug!r}.")
            if status and status not in allowed_statuses:
                add(findings, "ERROR", "REFERENCE_STATUS", row_number, filename, f"Unknown lifecycle status: {status!r}.")
            if order:
                if not order.isdigit() or int(order) <= 0:
                    add(findings, "ERROR", "REFERENCE_SORT_ORDER", row_number, filename, "sort_order must be a positive integer.")
                elif order in seen_orders:
                    add(findings, "ERROR", "DUPLICATE_SORT_ORDER", row_number, filename, f"sort_order duplicates row {seen_orders[order]}.")
                else:
                    seen_orders[order] = row_number
            if value:
                values.add(value)
                statuses[value] = status
        values_by_file[filename] = values
        statuses_by_file[filename] = statuses
    return findings, values_by_file, statuses_by_file


def find_cycle(edges: dict[str, list[str]]) -> list[str] | None:
    visiting: set[str] = set()
    visited: set[str] = set()
    path: list[str] = []

    def visit(node: str) -> list[str] | None:
        if node in visiting:
            start = path.index(node)
            return path[start:] + [node]
        if node in visited:
            return None
        visiting.add(node)
        path.append(node)
        for target in edges.get(node, []):
            cycle = visit(target)
            if cycle:
                return cycle
        path.pop()
        visiting.remove(node)
        visited.add(node)
        return None

    for candidate in edges:
        cycle = visit(candidate)
        if cycle:
            return cycle
    return None


def validate_v2(manifest: Path, references: Path) -> tuple[list[Finding], int]:
    findings, values_by_file, statuses_by_file = validate_reference_data(references)
    try:
        columns, rows = read_manifest(manifest)
    except (OSError, csv.Error) as exc:
        add(findings, "ERROR", "INPUT", None, "", str(exc))
        return sorted_findings(findings), 0

    missing = [column for column in V2_COLUMNS if column not in columns]
    extra = [column for column in columns if column not in V2_COLUMNS]
    for column in missing:
        add(findings, "ERROR", "MISSING_COLUMN", None, column, "Required schema v2 column is absent.")
    for column in extra:
        severity = "ERROR" if "confidence" in column.casefold() or "model_score" in column.casefold() else "WARNING"
        code = "PROHIBITED_AI_FIELD" if severity == "ERROR" else "EXTRA_COLUMN"
        add(findings, severity, code, None, column, "Column is not defined by schema v2.")
    if missing:
        return sorted_findings(findings), len(rows)

    names: defaultdict[str, list[tuple[int, str]]] = defaultdict(list)
    keys: defaultdict[str, list[int]] = defaultdict(list)
    alias_entries: list[tuple[int, str, str]] = []
    row_by_key: dict[str, tuple[int, dict[str, str]]] = {}

    for row_number, row in enumerate(rows, start=2):
        for field in V2_REQUIRED_VALUES:
            if not (row.get(field) or "").strip():
                add(findings, "ERROR", "REQUIRED_VALUE", row_number, field, "Required value is blank.")

        version = (row.get("schema_version") or "").strip()
        if not version:
            add(findings, "ERROR", "MISSING_SCHEMA_VERSION", row_number, "schema_version", "schema_version is required.")
        elif version != "2.0":
            add(findings, "ERROR", "WRONG_SCHEMA_VERSION", row_number, "schema_version", "schema_version must equal '2.0'.")

        key = (row.get("catalogue_key") or "").strip()
        if key:
            key_folded = key.casefold()
            keys[key_folded].append(row_number)
            row_by_key[key_folded] = (row_number, row)
            if not re.fullmatch(r"[a-z0-9]+(?:_[a-z0-9]+)*", key):
                add(findings, "ERROR", "KEY_FORMAT", row_number, "catalogue_key", "Use lowercase ASCII snake_case.")
        name = (row.get("canonical_name") or "").strip()
        if name:
            names[norm(name)].append((row_number, name))

        for field in V2_MULTI_VALUE_FIELDS:
            raw = row.get(field) or ""
            if has_empty_multi_value(raw):
                add(findings, "ERROR", "EMPTY_MULTI_VALUE", row_number, field, "Pipe-separated values may not contain empty elements.")
            values = split_values(raw)
            normalised = [norm(item) for item in values]
            if len(normalised) != len(set(normalised)):
                add(findings, "ERROR", "DUPLICATE_MULTI_VALUE", row_number, field, "A multi-value field repeats a value.")

        for field, filename in V2_REFERENCE_FILES.items():
            raw = (row.get(field) or "").strip()
            field_values = split_values(raw) if field in V2_MULTI_CONTROLLED_FIELDS else ([raw] if raw else [])
            allowed = values_by_file.get(filename, set())
            for value in field_values:
                if value not in allowed:
                    add(findings, "ERROR", "CONTROLLED_VALUE", row_number, field, f"Unknown value: {value!r}.")
                else:
                    lifecycle = statuses_by_file.get(filename, {}).get(value)
                    if lifecycle == "Retired":
                        add(findings, "ERROR", "RETIRED_CONTROLLED_VALUE", row_number, field, f"Retired value may not be used: {value!r}.")
                    elif lifecycle == "Deprecated" and (row.get("review_status") or "").strip() == "Approved":
                        add(findings, "ERROR", "DEPRECATED_APPROVED_VALUE", row_number, field, f"Approved records may not use deprecated value: {value!r}.")

        for field in V2_ALIAS_FIELDS:
            alias_entries.extend((row_number, field, alias) for alias in split_values(row.get(field) or ""))

        primary_patterns = split_values(row.get("primary_movement_pattern") or "")
        if len(primary_patterns) != 1:
            add(findings, "ERROR", "PRIMARY_PATTERN_COUNT", row_number, "primary_movement_pattern", "Exactly one primary movement pattern is required.")
        secondary_patterns = split_values(row.get("secondary_movement_patterns") or "")
        pattern_overlap = set(primary_patterns) & set(secondary_patterns)
        if pattern_overlap:
            add(findings, "ERROR", "MOVEMENT_PATTERN_OVERLAP", row_number, "secondary_movement_patterns", f"Movement patterns overlap: {sorted(pattern_overlap)!r}.")
        primary_actions = split_values(row.get("primary_joint_actions") or "")
        secondary_actions = split_values(row.get("secondary_joint_actions") or "")
        if not primary_actions:
            add(findings, "ERROR", "PRIMARY_JOINT_ACTION_REQUIRED", row_number, "primary_joint_actions", "At least one primary joint action is required.")
        action_overlap = set(primary_actions) & set(secondary_actions)
        if action_overlap:
            add(findings, "ERROR", "JOINT_ACTION_OVERLAP", row_number, "secondary_joint_actions", f"Joint actions overlap: {sorted(action_overlap)!r}.")

        primary_muscles = split_values(row.get("primary_muscles") or "")
        secondary_muscles = split_values(row.get("secondary_muscles") or "")
        stabilisers = split_values(row.get("stabiliser_muscles") or "")
        if not primary_muscles:
            add(findings, "ERROR", "PRIMARY_MUSCLE_REQUIRED", row_number, "primary_muscles", "At least one primary muscle is required.")
        muscle_overlap = (set(primary_muscles) & set(secondary_muscles)) | (set(primary_muscles) & set(stabilisers)) | (set(secondary_muscles) & set(stabilisers))
        if muscle_overlap:
            add(findings, "ERROR", "MUSCLE_OVERLAP", row_number, "primary_muscles", f"Muscle lists overlap: {sorted(muscle_overlap)!r}.")

        all_actions = set(primary_actions) | set(secondary_actions)
        name_folded = name.casefold()
        if "lateral raise" in name_folded:
            if "Shoulder Abduction" not in all_actions or "Trunk Lateral Flexion" in all_actions:
                add(findings, "ERROR", "LATERAL_RAISE_ACTION", row_number, "primary_joint_actions", "Lateral raises require Shoulder Abduction and must not use Trunk Lateral Flexion.")
        if "fly" in name_folded:
            required_action = "Horizontal Abduction" if "rear" in name_folded or "reverse" in name_folded else "Horizontal Adduction"
            if required_action not in all_actions:
                add(findings, "ERROR", "FLY_ACTION", row_number, "primary_joint_actions", f"Fly mechanics require {required_action}.")

        pattern = (row.get("primary_movement_pattern") or "").strip()
        if pattern == "Horizontal Push" and not ({"Horizontal Adduction", "Elbow Extension"} & all_actions):
            add(findings, "WARNING", "SUSPICIOUS_MOVEMENT", row_number, "primary_joint_actions", "Horizontal Push normally includes Horizontal Adduction or Elbow Extension.")
        if pattern == "Vertical Push" and not ({"Shoulder Flexion", "Shoulder Abduction"} & all_actions):
            add(findings, "WARNING", "SUSPICIOUS_MOVEMENT", row_number, "primary_joint_actions", "Vertical Push normally includes Shoulder Flexion or Shoulder Abduction.")
        if pattern == "Squat" and not ({"Hip Flexion", "Hip Extension"} & all_actions and {"Knee Flexion", "Knee Extension"} & all_actions):
            add(findings, "WARNING", "SUSPICIOUS_MOVEMENT", row_number, "primary_joint_actions", "Squat normally includes hip and knee flexion or extension actions.")
        if pattern == "Hinge" and not {"Hip Flexion", "Hip Extension"} <= all_actions:
            add(findings, "WARNING", "SUSPICIOUS_MOVEMENT", row_number, "primary_joint_actions", "Hinge normally includes Hip Flexion and Hip Extension.")

        equipment = set(split_values(row.get("equipment") or ""))
        bench_angle = (row.get("bench_angle") or "").strip()
        if (row.get("support_type") or "").strip() == "Bench Supported" and "Bench" not in equipment:
            add(findings, "ERROR", "BENCH_REQUIRED", row_number, "equipment", "Bench Supported records require Bench equipment.")
        if bench_angle and bench_angle != "Not Applicable" and "Bench" not in equipment:
            add(findings, "ERROR", "BENCH_REQUIRED", row_number, "bench_angle", "A bench angle requires Bench equipment.")
        if "Landmine" in equipment and not {"Barbell", "Landmine"} <= equipment:
            add(findings, "ERROR", "LANDMINE_EQUIPMENT", row_number, "equipment", "Landmine barbell records require both Barbell and Landmine.")
        grips = set(split_values(row.get("grip_type") or ""))
        if equipment & GRIPPED_EQUIPMENT and (not grips or grips == {"Not Applicable"}):
            add(findings, "ERROR", "GRIP_REQUIRED", row_number, "grip_type", "Load-bearing gripped equipment requires grip_type.")

        loading_positions = set(split_values(row.get("loading_position") or ""))
        external_load = (row.get("external_load") or "").strip()
        if ("Bodyweight" in equipment or "Bodyweight" in loading_positions) and external_load not in {"Bodyweight Only", "Optional External Load"}:
            add(findings, "ERROR", "BODYWEIGHT_LOAD", row_number, "external_load", "Bodyweight loading conflicts with external_load.")
        if external_load == "Bodyweight Only" and "Bodyweight" not in equipment and "Bodyweight" not in loading_positions:
            add(findings, "ERROR", "BODYWEIGHT_LOAD", row_number, "external_load", "Bodyweight Only requires Bodyweight equipment or loading position.")
        if (row.get("laterality") or "").strip() == "Unilateral" and re.search(r"\b(bilateral|two[- ]arm|two[- ]leg)\b", name_folded):
            add(findings, "ERROR", "LATERALITY_NAME", row_number, "laterality", "Unilateral classification conflicts with the canonical name.")
        if (row.get("compound_or_isolation") or "").strip() == "Isolation" and len(primary_actions) > 1:
            add(findings, "WARNING", "ISOLATION_ACTIONS", row_number, "primary_joint_actions", "Isolation exercises normally have one primary joint action.")

        review_status = (row.get("review_status") or "").strip()
        clinical_status = (row.get("clinical_review_status") or "").strip()
        coaching_status = (row.get("coaching_review_status") or "").strip()
        origin = (row.get("content_origin") or "").strip()
        assistance = split_values(row.get("ai_assistance_tasks") or "")
        source = (row.get("source_provenance") or "").strip()
        contraindications = set(split_values(row.get("contraindication_flags") or "")) - {"No Catalogue Flag"}
        if clinical_status in {"Required", "In Review"} and review_status == "Approved":
            add(findings, "ERROR", "CLINICAL_APPROVAL", row_number, "review_status", "Incomplete clinical review prevents approval.")
        if coaching_status not in {"Not Required", "Completed"} and review_status == "Approved":
            add(findings, "ERROR", "COACHING_APPROVAL", row_number, "review_status", "Incomplete coaching review prevents approval.")
        if origin in {"AI Generated — Review Pending", "Imported — Review Pending"} and review_status == "Approved":
            add(findings, "ERROR", "AI_APPROVAL", row_number, "review_status", "Review-pending content cannot be Approved.")
        if origin in AI_ORIGINS and not assistance:
            add(findings, "ERROR", "AI_TASKS_REQUIRED", row_number, "ai_assistance_tasks", "AI-assisted content must declare assistance tasks.")
        if review_status == "Approved" and split_values(row.get("ai_review_flags") or ""):
            add(findings, "ERROR", "AI_REVIEW_FLAGS", row_number, "review_status", "Outstanding AI review flags prevent approval.")
        if origin in AI_ORIGINS and any((row.get(field) or "").strip() for field in COACHING_FIELDS) and coaching_status != "Completed":
            add(findings, "ERROR", "AI_COACHING_REVIEW", row_number, "coaching_review_status", "AI-generated coaching text requires completed coaching review.")
        if contraindications:
            if not source:
                add(findings, "ERROR", "CLINICAL_PROVENANCE", row_number, "source_provenance", "Contraindication claims require source provenance.")
            if clinical_status == "Not Required":
                add(findings, "ERROR", "CLINICAL_REVIEW", row_number, "clinical_review_status", "Contraindication claims require clinical review.")

        verified_fields = split_values(row.get("human_verified_fields") or "")
        for field in verified_fields:
            if field not in V2_COLUMNS:
                add(findings, "ERROR", "VERIFIED_FIELD", row_number, "human_verified_fields", f"Unknown field name: {field!r}.")
        for field in V2_COLUMNS:
            value = row.get(field) or ""
            if re.search(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", value, re.IGNORECASE):
                add(findings, "ERROR", "PERSONAL_DATA", row_number, field, "Canonical catalogue records must not contain email addresses.")

    for key, line_numbers in keys.items():
        if len(line_numbers) > 1:
            for row_number in line_numbers:
                add(findings, "ERROR", "DUPLICATE_KEY", row_number, "catalogue_key", f"Duplicate key {key!r}.")
    for occurrences in names.values():
        if len(occurrences) > 1:
            for row_number, value in occurrences:
                add(findings, "ERROR", "DUPLICATE_NAME", row_number, "canonical_name", f"Duplicate canonical name {value!r}.")

    identity_lookup = set(names) | {norm(key) for key in keys}
    for row_number, field, alias in alias_entries:
        if norm(alias) in identity_lookup:
            add(findings, "ERROR", "ALIAS_COLLISION", row_number, field, f"Alias {alias!r} collides with a canonical name or key.")

    known_keys = set(keys)
    retired_keys = {value.casefold() for value in values_by_file.get("retired-keys.csv", set())}
    for key, (row_number, _) in row_by_key.items():
        if key in retired_keys:
            add(findings, "ERROR", "RETIRED_KEY_REUSE", row_number, "catalogue_key", "A retired catalogue key may not be reused.")
    for key, (row_number, row) in row_by_key.items():
        for field in V2_KEY_REFERENCE_FIELDS:
            references_in_field = split_values(row.get(field) or "")
            for target in references_in_field:
                target_folded = target.casefold()
                if target_folded == key:
                    add(findings, "ERROR", "SELF_REFERENCE", row_number, field, "A record may not reference itself.")
                elif target_folded not in known_keys and not (field == "supersedes_key" and target_folded in retired_keys):
                    add(findings, "ERROR", "UNRESOLVED_REFERENCE", row_number, field, f"Unknown catalogue key: {target!r}.")

    graph_fields = {
        "parent_exercise_key": "PARENT_CYCLE",
        "progression_keys": "PROGRESSION_CYCLE",
        "regression_keys": "REGRESSION_CYCLE",
    }
    for field, code in graph_fields.items():
        edges = {
            key: [target.casefold() for target in split_values(row.get(field) or "") if target.casefold() in known_keys]
            for key, (_, row) in row_by_key.items()
        }
        cycle = find_cycle(edges)
        if cycle:
            add(findings, "ERROR", code, None, field, f"Cycle detected: {' -> '.join(cycle)}.")

    return sorted_findings(findings), len(rows)


def validate(manifest: Path, references: Path) -> tuple[list[Finding], int]:
    """Backward-compatible schema v1 API used by existing tests."""
    return validate_v1(manifest, references)


def sorted_findings(findings: list[Finding]) -> list[Finding]:
    return sorted(findings, key=lambda item: (item.severity != "ERROR", item.row or 0, item.code, item.field))


def markdown_report(manifest: Path, row_count: int, findings: list[Finding], schema_version: str) -> str:
    counts = Counter(item.severity for item in findings)
    status = "PASS" if counts["ERROR"] == 0 else "FAIL"
    display_manifest = Path(os.path.relpath(manifest.resolve(), Path.cwd().resolve())).as_posix()
    lines = [
        "# Catalogue Validation Audit", "",
        f"- Status: **{status}**",
        f"- Schema version: **{schema_version}**",
        f"- Manifest: `{display_manifest}`",
        f"- Candidate rows: **{row_count}**",
        f"- Errors: **{counts['ERROR']}**",
        f"- Warnings: **{counts['WARNING']}**", "",
    ]
    if not findings:
        lines.extend(["## Findings", "", "No findings.", ""])
        return "\n".join(lines)
    lines.extend(["## Findings", "", "| Severity | Code | Row | Field | Message |", "|---|---|---:|---|---|"])
    for item in findings:
        message = item.message.replace("|", "\\|")
        lines.append(f"| {item.severity} | {item.code} | {item.row or '—'} | `{item.field}` | {message} |")
    lines.append("")
    return "\n".join(lines)


def main(argv=None) -> int:
    workspace = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--schema-version", choices=("1", "2"), required=True)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--references", type=Path)
    parser.add_argument("--report", type=Path, default=Path("catalogue/catalogue-audit.md"))
    args = parser.parse_args(argv)

    if args.schema_version == "1":
        manifest = args.manifest or workspace / "catalogue" / "candidate-manifest.csv"
        references = args.references or workspace / "catalogue" / "reference"
        findings, row_count = validate_v1(manifest, references)
    else:
        manifest = args.manifest or workspace / "catalogue" / "candidate-manifest-v2.csv"
        references = args.references or workspace / "catalogue" / "reference" / "v2"
        findings, row_count = validate_v2(manifest, references)

    report_path = args.report if args.report.is_absolute() else workspace / args.report
    report = markdown_report(manifest, row_count, findings, args.schema_version)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report, encoding="utf-8")
    errors = sum(item.severity == "ERROR" for item in findings)
    warnings = sum(item.severity == "WARNING" for item in findings)
    print(f"Validated schema v{args.schema_version} with {row_count} row(s): {errors} error(s), {warnings} warning(s).")
    print(f"Audit report: {args.report}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
