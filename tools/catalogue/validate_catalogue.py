#!/usr/bin/env python3
"""Validate the Human Strength candidate manifest and write a Markdown audit."""

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

REQUIRED_COLUMNS = [
    "catalogue_key", "exercise_family", "parent_exercise", "canonical_name",
    "variation_type", "equipment", "primary_movement_pattern",
    "secondary_movement_pattern", "primary_muscle_group",
    "secondary_muscle_groups", "difficulty", "technical_complexity",
    "facility_tier", "commercial_importance", "launch_priority",
    "search_aliases", "manufacturer_aliases", "regional_aliases",
    "future_module_tags", "substitution_group", "review_status", "review_notes",
]
REQUIRED_VALUES = [
    "catalogue_key", "exercise_family", "parent_exercise", "canonical_name",
    "variation_type", "equipment", "primary_movement_pattern",
    "primary_muscle_group", "secondary_muscle_groups", "difficulty",
    "technical_complexity", "facility_tier", "commercial_importance",
    "launch_priority", "review_status", "review_notes",
]
CONTROLLED_SINGLE = {
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
CONTROLLED_MULTI = {
    "secondary_muscle_groups": "muscle_group",
    "future_module_tags": "future_module_tags",
}
ALIAS_FIELDS = ("search_aliases", "manufacturer_aliases", "regional_aliases")
MULTI_VALUE_FIELDS = ("equipment", *CONTROLLED_MULTI, *ALIAS_FIELDS)


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


def load_inputs(manifest: Path, references: Path):
    with (references / "controlled-values.json").open(encoding="utf-8") as handle:
        controlled = json.load(handle)
    with (references / "equipment.csv").open(newline="", encoding="utf-8-sig") as handle:
        equipment = {row["equipment"].strip() for row in csv.DictReader(handle)}
    with manifest.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        return reader.fieldnames or [], list(reader), controlled, equipment


def validate(manifest: Path, references: Path) -> tuple[list[Finding], int]:
    findings: list[Finding] = []
    try:
        columns, rows, controlled, equipment_values = load_inputs(manifest, references)
    except (OSError, csv.Error, json.JSONDecodeError, KeyError) as exc:
        return [Finding("ERROR", "INPUT", None, "", str(exc))], 0

    missing = [column for column in REQUIRED_COLUMNS if column not in columns]
    extra = [column for column in columns if column not in REQUIRED_COLUMNS]
    for column in missing:
        add(findings, "ERROR", "MISSING_COLUMN", None, column, "Required CSV column is absent.")
    for column in extra:
        add(findings, "WARNING", "EXTRA_COLUMN", None, column, "Column is not defined by the architecture.")
    if missing:
        return findings, len(rows)

    names: defaultdict[str, list[tuple[int, str]]] = defaultdict(list)
    keys: defaultdict[str, list[int]] = defaultdict(list)
    aliases: list[tuple[int, str, str]] = []

    for index, row in enumerate(rows, start=2):
        for field in REQUIRED_VALUES:
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

        for field in MULTI_VALUE_FIELDS:
            value = row.get(field) or ""
            if has_empty_multi_value(value):
                add(findings, "ERROR", "EMPTY_MULTI_VALUE", index, field, "Pipe-separated values may not contain empty elements.")

        for field, vocabulary in CONTROLLED_SINGLE.items():
            value = (row.get(field) or "").strip()
            if value and value not in controlled[vocabulary]:
                add(findings, "ERROR", "CONTROLLED_VALUE", index, field, f"Unknown value: {value!r}.")

        for field, vocabulary in CONTROLLED_MULTI.items():
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

        for field in ALIAS_FIELDS:
            field_aliases = split_values(row.get(field) or "")
            if len(field_aliases) != len({norm(alias) for alias in field_aliases}):
                add(findings, "WARNING", "REPEATED_ALIAS", index, field, "Alias is repeated in this field.")
            aliases.extend((index, field, alias) for alias in field_aliases)

    for key, line_numbers in keys.items():
        if len(line_numbers) > 1:
            for row_number in line_numbers:
                add(findings, "ERROR", "DUPLICATE_KEY", row_number, "catalogue_key", f"Duplicate key {key!r}.")
    for normalized_name, occurrences in names.items():
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

    return sorted(findings, key=lambda f: (f.severity != "ERROR", f.row or 0, f.code, f.field)), len(rows)


def markdown_report(manifest: Path, row_count: int, findings: list[Finding]) -> str:
    counts = Counter(item.severity for item in findings)
    status = "PASS" if counts["ERROR"] == 0 else "FAIL"
    display_manifest = Path(os.path.relpath(manifest.resolve(), Path.cwd().resolve())).as_posix()
    lines = [
        "# Catalogue Validation Audit", "",
        f"- Status: **{status}**",
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
    parser.add_argument("--manifest", type=Path, default=workspace / "catalogue" / "candidate-manifest.csv")
    parser.add_argument("--references", type=Path, default=workspace / "catalogue" / "reference")
    parser.add_argument("--report", type=Path, default=workspace / "catalogue" / "catalogue-audit.md")
    args = parser.parse_args(argv)
    findings, row_count = validate(args.manifest, args.references)
    report = markdown_report(args.manifest, row_count, findings)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(report, encoding="utf-8")
    errors = sum(item.severity == "ERROR" for item in findings)
    warnings = sum(item.severity == "WARNING" for item in findings)
    print(f"Validated {row_count} rows: {errors} error(s), {warnings} warning(s).")
    print(f"Audit report: {args.report}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
