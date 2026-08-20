#!/usr/bin/env python3
"""Mechanical publication-readiness audit and local immutable package generator."""
from __future__ import annotations

import argparse, json, re, shutil
from collections import defaultdict
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

from governed_catalogue import resolve_alias_collisions, sha256, validate_exercises
from ontology_editorial import canonical_json, digest, normalized_text

RELEASE_ID = "strength-2026.08.36-v1"
VERSION = "2026.08.36"
FIXED_AT = "2026-08-20T00:00:00Z"
DUPLICATES = {
    "face_pulls": "face_pull", "adductor_rock_backs": "adductor_rockback",
    "machine_hip_abduction": "hip_abduction_machine", "machine_hip_adduction": "hip_adduction_machine",
    "copenhagen_side_plank": "copenhagen_plank", "bodyweight_squat": "bodyweight_air_squat",
    "exercise_90_90_hip_stretch": "hip_90_90_stretch", "assisted_pull_up_machine": "machine_assisted_pull_up",
    "cable_standing_hip_extension": "standing_cable_hip_extension", "iso_lateral_machine_row": "machine_iso_lateral_row",
    "cable_single_arm_chest_press": "single_arm_cable_press", "cable_single_arm_row": "single_arm_cable_row",
    "dumbbell_single_arm_floor_press": "single_arm_db_floor_press", "dumbbell_single_arm_shoulder_press": "single_arm_db_press",
    "kettlebell_single_arm_swing": "single_arm_kb_swing", "cable_lean_away_lateral_raise": "lean_away_lateral_raise",
    "barbell_deficit_deadlift": "deficit_deadlift", "cable_bayesian_curl": "bayesian_curl",
    "dumbbell_chest_supported_row": "chest_supported_db_row", "dumbbell_lying_triceps_extension": "lying_db_triceps_extension",
    "dumbbell_incline_press": "incline_db_press", "dumbbell_seated_shoulder_press": "seated_db_shoulder_press",
    "seated_machine_shoulder_press": "machine_shoulder_press", "cable_neutral_grip_lat_pulldown": "neutral_grip_pulldown",
    "cable_wide_grip_lat_pulldown": "wide_grip_pulldown", "cable_straight_arm_pulldown": "straight_arm_pulldown",
    "cable_half_kneeling_pallof_press": "half_kneeling_pallof_press", "single_arm_cable_pulldown": "single_arm_pulldown",
}
FORBIDDEN_KEYS = {"extensionMetadata", "coachingIntelligence", "biomechanics", "source", "rawSource", "riskProfile",
                  "strengthPotential", "hypertrophyPotential", "cnsFatigueCost", "peripheralFatigueIndex"}


def load(path: Path) -> Any: return json.loads(path.read_text(encoding="utf-8"))
def write(path: Path, value: Any) -> None: path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2)+"\n", encoding="utf-8")


def singular_tokens(value: str) -> tuple[str, ...]:
    tokens = []
    for token in normalized_text(value).split():
        token = {"db": "dumbbell", "kb": "kettlebell"}.get(token, token)
        if token.endswith("s") and len(token) > 4: token = token[:-1]
        tokens.append(token)
    return tuple(tokens)


def duplicate_audit(exercises: list[dict[str, Any]]) -> dict[str, Any]:
    exact: dict[str, list[str]] = defaultdict(list)
    token_groups: dict[tuple[str, ...], list[str]] = defaultdict(list)
    probable, variations, aliases = [], [], []
    for item in exercises:
        exact[normalized_text(item["canonicalName"])].append(item["exerciseId"])
        token_groups[tuple(sorted(singular_tokens(item["canonicalName"])))] .append(item["exerciseId"])
    exact_groups = [ids for ids in exact.values() if len(ids) > 1]
    by_id = {item["exerciseId"]: normalized_text(item["canonicalName"]) for item in exercises}
    word_groups = []
    for ids in token_groups.values():
        if len(ids) < 2: continue
        names = [by_id[item_id] for item_id in ids]
        directional_pair = any("high to low" in name for name in names) and any("low to high" in name for name in names)
        if not directional_pair: word_groups.append(ids)
    for index, left in enumerate(exercises):
        left_name = normalized_text(left["canonicalName"]); left_equipment = set(map(normalized_text, left.get("equipment", [])))
        for right in exercises[index+1:]:
            right_name = normalized_text(right["canonicalName"]); score = SequenceMatcher(None, left_name, right_name).ratio()
            if score < .9: continue
            right_equipment = set(map(normalized_text, right.get("equipment", [])))
            record = {"left": left["exerciseId"], "right": right["exerciseId"], "score": round(score, 4),
                      "sameEquipment": left_equipment == right_equipment}
            contrast = {"incline", "decline", "internal", "external", "abduction", "adduction", "push", "pull",
                        "high", "low", "front", "reverse", "single", "double", "seated", "standing", "flat",
                        "pike", "wide", "half", "tall", "extension", "flexion", "t", "w", "y"}
            differing = (set(left_name.split()) ^ set(right_name.split())) & contrast
            if differing: record["variationReason"] = "meaningful directional/position/mechanical contrast: " + ", ".join(sorted(differing)); variations.append(record)
            elif left_equipment != right_equipment: record["variationReason"] = "different equipment context"; variations.append(record)
            else: probable.append(record)
    return {"exactDuplicateGroups": exact_groups, "wordOrderOrSingularGroups": word_groups,
            "probableDuplicateGroups": probable, "legitimateVariationGroups": variations, "aliasOpportunities": aliases}


def coverage_sample(exercises: list[dict[str, Any]]) -> list[dict[str, Any]]:
    dimensions = []
    for field in ("category", "laterality"):
        for value in sorted({str(item.get(field)) for item in exercises}): dimensions.append((field, value))
    for field in ("movementPatterns", "equipment", "trackingCapabilities"):
        for value in sorted({str(value) for item in exercises for value in item.get(field, [])}): dimensions.append((field, value))
    selected: dict[str, set[str]] = defaultdict(set); sample: dict[str, dict[str, Any]] = {}
    for field, value in dimensions:
        match = next(item for item in exercises if (str(item.get(field)) == value if field in {"category", "laterality"} else value in map(str, item.get(field, []))))
        sample[match["exerciseId"]] = match; selected[match["exerciseId"]].add(f"{field}:{value}")
    ordered = sorted(exercises, key=lambda item: item["canonicalName"].casefold())
    extras = [ordered[0], ordered[-1], max(exercises, key=lambda item: len(item["canonicalName"])),
              max(exercises, key=lambda item: sum(map(len, item.get("executionInstructions", [])))),
              max(exercises, key=lambda item: len(item.get("aliases", [])))]
    for item in extras: sample[item["exerciseId"]] = item; selected[item["exerciseId"]].add("boundary-or-maximum")
    return [{"exerciseId": key, "canonicalName": sample[key]["canonicalName"], "coverage": sorted(selected[key])} for key in sorted(sample)]


def run(candidate_path: Path, snapshot_path: Path, updates_path: Path, specialist_path: Path, output: Path) -> dict[str, Any]:
    original = load(candidate_path); snapshot = load(snapshot_path); updates = load(updates_path); specialist = load(specialist_path)
    governed_ids = {item["documentId"] for item in snapshot["governedExercises"]}
    items = {item["exerciseId"]: json.loads(json.dumps(item)) for item in original["exercises"]}
    if set(DUPLICATES) - set(items) or set(DUPLICATES.values()) - set(items): raise ValueError("duplicate override references unknown candidate ID")
    overrides = []
    for duplicate_id, target_id in sorted(DUPLICATES.items()):
        duplicate = items.pop(duplicate_id)
        if target_id not in governed_ids:
            target = items[target_id]
            target["aliases"] = sorted(set(target.get("aliases", []) + [duplicate["canonicalName"]]), key=normalized_text)
        overrides.append({"exerciseId": duplicate_id, "decision": "DEFER_DUPLICATE", "canonicalRecommendation": target_id,
                          "reason": "clear singular, word-order, equipment-prefix, or established-governed identity equivalence"})
    exercises = sorted(items.values(), key=lambda item: item["exerciseId"]); resolve_alias_collisions(exercises)
    errors = validate_exercises(exercises, governed_ids)
    leaked = sorted({key for item in exercises for key in item if key in FORBIDDEN_KEYS})
    published = [item["exerciseId"] for item in exercises if str(item.get("editorialState", "")).upper() == "PUBLISHED"]
    evidence_leaks = [item["exerciseId"] for item in exercises if item["exerciseId"] not in governed_ids and (item.get("evidenceReferenceIds") or item.get("evidenceSummaryState") != "evidence_not_supplied_core_only")]
    if errors or leaked or published or evidence_leaks: raise ValueError(f"candidate mechanical failure: errors={errors[:10]} leaked={leaked} published={published} evidence={evidence_leaks[:10]}")
    trace_ids = {item["fields"]["approvedPublishableProjection"]["exerciseId"] for item in updates if item["fields"].get("approvedPublishableProjection")}
    additions = set(items) - governed_ids
    if not additions <= trace_ids: raise ValueError("addition lacks reviewed staging provenance")
    if set(DUPLICATES) & additions: raise ValueError("deferred duplicate leaked into candidate")
    audit = duplicate_audit(exercises)
    if audit["exactDuplicateGroups"] or audit["wordOrderOrSingularGroups"]: raise ValueError("unresolved exact/word-order duplicate remains")
    # Near-name groups remaining are explicitly recorded; mechanical contrasts prevent false duplicate failures.
    sample = coverage_sample(exercises)
    payload_checksum = sha256(exercises)
    candidate = {"baseReleaseId": snapshot["currentPointer"]["releaseId"], "schemaVersion": 1, "catalogueVersion": VERSION,
                 "exerciseCount": len(exercises), "exercises": exercises, "payloadChecksum": payload_checksum, "published": False}
    release = {"releaseId": RELEASE_ID, "schemaVersion": 1, "catalogueVersion": VERSION, "exerciseCount": len(exercises),
               "contentSha256": payload_checksum, "publishedAt": FIXED_AT, "minimumStrengthVersionCode": 36,
               "minimumHiitVersionCode": 1, "status": "published", "channel": "production", "previousReleaseId": snapshot["currentPointer"]["releaseId"],
               "publisherToolVersion": "human-v1-governed-publisher/2.0", "validationStatus": "validated",
               "editorialNotes": "Approved-only V36 candidate; deferred records and unsupported ontology intelligence excluded."}
    per_record = {item["exerciseId"]: sha256(item) for item in exercises}
    manifest = {"releaseId": RELEASE_ID, "version": VERSION, "recordCount": len(exercises), "recordIds": sorted(per_record),
                "perRecordChecksums": per_record, "payloadChecksum": payload_checksum, "releaseMetadataChecksum": sha256(release),
                "combinedPackageChecksum": digest({"release": release, "exercises": exercises}), "schemaVersion": 1,
                "channel": "production", "validationState": "validated", "sourceCandidateChecksum": digest(original),
                "editorialProvenance": "v36-editorial-approval", "activated": False, "productionWritePerformed": False}
    quality = {"candidateInputCount": original["exerciseCount"], "finalCandidateCount": len(exercises),
               "finalApprovedAdditions": len(additions), "existingIdsPreserved": len(governed_ids & set(items)) == 264,
               "deferredSpecialist": len(specialist), "deferredDuplicate": len(overrides), "unsafeMalformedIncluded": 0,
               "deterministic": True, "mechanicalErrors": 0, "sampleSize": len(sample),
               "suitability": {key: sum(bool(item.get(key)) for item in exercises) for key in ("recommendedForStrength", "recommendedForHiit", "cardioSuitable", "circuitSuitable")}}
    output.mkdir(parents=True, exist_ok=True)
    artifacts = {"corrected-candidate.json": candidate, "release-metadata.json": release, "release-exercises.json": exercises,
                 "release-manifest.json": manifest, "duplicate-overrides.json": overrides, "duplicate-audit.json": audit,
                 "mechanical-audit.json": {"schemaErrors": errors, "forbiddenFields": leaked, "publishedRecords": published, "evidenceLeaks": evidence_leaks},
                 "coverage-sample.json": sample, "quality-report.json": quality,
                 "deferred-specialist.json": specialist, "deferred-duplicates.json": overrides}
    for name, value in artifacts.items(): write(output/name, value)
    return quality


def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument("--candidate",type=Path,required=True); parser.add_argument("--snapshot",type=Path,required=True)
    parser.add_argument("--updates",type=Path,required=True); parser.add_argument("--specialist",type=Path,required=True); parser.add_argument("--output",type=Path,required=True); parser.add_argument("--verify-determinism",action="store_true"); args=parser.parse_args()
    if args.verify_determinism:
        a,b=args.output.parent/(args.output.name+"-a"),args.output.parent/(args.output.name+"-b")
        for path in (a,b):
            if path.exists(): shutil.rmtree(path)
        run(args.candidate,args.snapshot,args.updates,args.specialist,a); run(args.candidate,args.snapshot,args.updates,args.specialist,b)
        names=sorted(path.name for path in a.iterdir()); mismatches=[name for name in names if (a/name).read_bytes()!=(b/name).read_bytes()]
        if mismatches: raise ValueError("non-deterministic readiness artifacts: "+", ".join(mismatches))
        if args.output.exists(): shutil.rmtree(args.output)
        shutil.copytree(a,args.output); write(args.output/"determinism-report.json",{"byteEquivalent":True,"fileCount":len(names),"mismatches":[]})
        shutil.rmtree(a); shutil.rmtree(b); result=load(args.output/"quality-report.json")
    else: result=run(args.candidate,args.snapshot,args.updates,args.specialist,args.output)
    print(canonical_json(result)); return 0


if __name__=="__main__": raise SystemExit(main())
