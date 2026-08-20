#!/usr/bin/env python3
"""Deterministic first-pass approval of V36 ontology staging records."""
from __future__ import annotations

import argparse
import json
import re
import shutil
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

from governed_catalogue import resolve_alias_collisions, sha256, validate_exercises
from ontology_editorial import canonical_json, digest, normalized_text

REVIEWER = "CODEX_EDITORIAL_REVIEW"
REVIEW_SCHEMA = "v36-editorial-approval-1"
FIXED_REVIEW_DATE = "2026-08-20T00:00:00Z"
DECISIONS = {
    "APPROVE_CORE", "APPROVE_FULL", "DEFER_DUPLICATE", "DEFER_SPECIALIST",
    "REJECT_MALFORMED", "REJECT_UNSAFE", "REJECT_NOT_EXERCISE",
}
SPECIALIST_CATEGORIES = {
    "rehabilitation", "olympic weightlifting", "olympic_weightlifting", "plyometrics",
}
SPECIALIST_TERMS = {
    "rehab", "post operative", "post op", "cervical", "neck bridge", "guillotine press",
    "muscle up", "handstand", "depth jump", "drop jump", "snatch", "clean and jerk",
    "push jerk", "split jerk", "power clean", "muscle clean", "hang clean", "power snatch",
}
EXCLUDED_ADVANCED = [
    "extensionMetadata.ai_coaching_engine.cns_fatigue_cost",
    "extensionMetadata.ai_coaching_engine.hypertrophy_potential",
    "extensionMetadata.ai_coaching_engine.peripheral_fatigue_index",
    "extensionMetadata.ai_coaching_engine.risk_profile.common_injury_vectors",
    "extensionMetadata.ai_coaching_engine.spinal_loading_vector",
    "extensionMetadata.ai_coaching_engine.strength_potential",
    "extensionMetadata.ai_coaching_engine.technical_complexity",
    "extensionMetadata.anatomy.primary_muscles[].involvement_percentage",
    "extensionMetadata.wearable_telemetry",
    "biomechanics",
    "coachingIntelligence",
    "evidence",
]


def load(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def clean_values(values: Any) -> list[str]:
    if not isinstance(values, list):
        return []
    return sorted({str(value).strip().lower() for value in values if str(value).strip()}, key=normalized_text)


def normalized_category(value: Any) -> str:
    text = normalized_text(value)
    mappings = {
        "push horizontal": "Chest", "horizontal push": "Chest", "pull horizontal": "Back",
        "horizontal pull": "Back", "push vertical": "Shoulders", "vertical push": "Shoulders",
        "pull vertical": "Back", "vertical pull": "Back", "hinge": "Posterior Chain",
        "squat": "Legs", "lunge": "Legs", "isolation": "Accessory",
        "flexibility": "Mobility", "stretching": "Mobility", "gait": "Cardio",
        "olympic weightlifting": "Olympic Weightlifting",
    }
    return mappings.get(text, " ".join(word.capitalize() for word in text.split()) or "General")


def capabilities(exercise: dict[str, Any], category: str) -> list[str]:
    name = normalized_text(exercise["displayName"])
    equipment = set(map(normalized_text, exercise.get("equipment", [])))
    cat = normalized_text(category)
    bodyweight = not equipment or equipment <= {"bodyweight", "none"} or "bodyweight" in name
    if cat in {"mobility", "cardio"} or any(term in name for term in ("hold", "stretch", "walk", "run", "cycle")):
        result = {"duration", "rpe"}
        if any(term in name for term in ("walk", "run", "row", "cycle", "ski")):
            result.add("distance")
    else:
        result = {"repetitions", "rpe", "tempo"}
        result.add("bodyweight" if bodyweight else "load")
    return sorted(result)


def projection(exercise: dict[str, Any]) -> dict[str, Any]:
    name = str(exercise["displayName"]).strip()
    category = normalized_category(exercise.get("category"))
    equipment = clean_values(exercise.get("equipment")) or ["bodyweight"]
    caps = capabilities(exercise, category)
    is_cardio = category == "Cardio"
    is_mobility = category == "Mobility"
    is_bodyweight = "bodyweight" in caps
    aliases = [value for value in exercise.get("aliases", []) if normalized_text(value) and normalized_text(value) != normalized_text(name)]
    movement = clean_values(exercise.get("movementPatterns")) or [normalized_text(category)]
    setup_equipment = ", ".join(equipment)
    item = {
        "exerciseId": exercise["proposedCanonicalStableId"],
        "schemaVersion": 1,
        "canonicalName": name,
        "displayName": name,
        "aliases": sorted(set(aliases), key=normalized_text),
        "summary": f"A {movement[0]} exercise using {setup_equipment}.",
        "category": category,
        "exerciseType": "cardio" if is_cardio else "mobility" if is_mobility else "bodyweight" if is_bodyweight else "strength",
        "movementPatterns": movement,
        "primaryMuscles": clean_values(exercise.get("primaryMuscles")),
        "secondaryMuscles": clean_values(exercise.get("secondaryMuscles")),
        "equipment": equipment,
        "laterality": exercise.get("laterality") if exercise.get("laterality") in {"bilateral", "unilateral"} else "bilateral",
        "trackingCapabilities": caps,
        "timedCompatible": "duration" in caps,
        "recommendedForStrength": not is_cardio and not is_mobility,
        "recommendedForHiit": is_cardio or (is_bodyweight and not is_mobility),
        "cardioSuitable": is_cardio,
        "circuitSuitable": is_cardio or (not is_mobility and (is_bodyweight or "repetitions" in caps)),
        "timedIntervalSuitable": "duration" in caps,
        "gymSuitable": any(value not in {"bodyweight", "none", "resistance band", "dumbbell", "kettlebell"} for value in equipment),
        "homeSuitable": all(value in {"bodyweight", "none", "resistance band", "dumbbell", "kettlebell", "bench"} for value in equipment),
        "setupInstructions": f"Use a stable area and prepare the {setup_equipment}. Choose a comfortable starting position and a manageable range.",
        "executionInstructions": [
            f"Perform {name} with a controlled, pain-free range.",
            "Keep the working joints aligned and avoid using uncontrolled momentum.",
            "Return to the starting position under control and reset before continuing.",
        ],
        "breathingGuidance": "Breathe steadily. Exhale through the main effort and avoid holding your breath unnecessarily.",
        "techniqueCues": ["Use a controlled range", "Keep the working joints aligned", "Stop the set when technique changes"],
        "commonMistakes": ["Using uncontrolled momentum", "Continuing after technique or range deteriorates"],
        "safetyGuidance": "Use a manageable resistance and a pain-free range. Stop for sharp pain, dizziness, numbness, or unusual discomfort and seek qualified guidance when appropriate.",
        "contraindicationCautions": [],
        "progressionIds": [], "regressionIds": [], "relatedExerciseIds": [],
        "deprecated": False, "replacementExerciseId": None,
        "evidenceSummaryState": "evidence_not_supplied_core_only", "evidenceReferenceIds": [],
        "localizationReady": True, "defaultLocale": "en-GB", "availableLocales": ["en-GB"],
        "contentRevision": 1, "editorialStatus": "approved",
        "createdAt": FIXED_REVIEW_DATE, "updatedAt": FIXED_REVIEW_DATE,
        "normalizedNames": sorted({normalized_text(name), *(normalized_text(alias) for alias in aliases)}),
        "suppressedAliases": [],
    }
    return item


def duplicate_pairs(records: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    deferred: dict[str, dict[str, Any]] = {}
    ordered = sorted(records, key=lambda value: value["documentId"])
    alias_sets = {item["documentId"]: {normalized_text(v) for v in item["fields"]["exercise"].get("aliases", []) if normalized_text(v)} for item in ordered}
    for index, left in enumerate(ordered):
        left_id, left_ex = left["documentId"], left["fields"]["exercise"]
        if left_id in deferred:
            continue
        left_name = normalized_text(left_ex["displayName"])
        left_equipment = set(map(normalized_text, left_ex.get("equipment", [])))
        for right in ordered[index + 1:]:
            right_id, right_ex = right["documentId"], right["fields"]["exercise"]
            right_name = normalized_text(right_ex["displayName"])
            right_equipment = set(map(normalized_text, right_ex.get("equipment", [])))
            exact_alias = right_name in alias_sets[left_id] or left_name in alias_sets[right_id]
            shared_alias = bool(alias_sets[left_id] & alias_sets[right_id])
            similarity = SequenceMatcher(None, left_name, right_name).ratio()
            same_context = left_equipment == right_equipment and normalized_text(left_ex.get("laterality")) == normalized_text(right_ex.get("laterality"))
            if same_context and (exact_alias or shared_alias or similarity >= .965):
                deferred[right_id] = {
                    "duplicateTarget": left_id,
                    "reasons": [f"candidate identity overlap score {similarity:.4f}", "same equipment and laterality context"],
                    "humanDecisionRequired": "Decide whether this is an alias, meaningful variation, or distinct canonical exercise.",
                }
    return deferred


def specialist_reasons(exercise: dict[str, Any]) -> list[str]:
    reasons: list[str] = []
    category = normalized_text(exercise.get("category"))
    name = normalized_text(exercise.get("displayName"))
    risk = normalized_text(((exercise.get("safety") or {}).get("riskProfile") or {}).get("overall_risk"))
    if category in {normalized_text(value) for value in SPECIALIST_CATEGORIES}:
        reasons.append(f"specialist category requires professional technique review: {exercise.get('category')}")
    matched = sorted(term for term in SPECIALIST_TERMS if term in name)
    if matched:
        reasons.append("high-complexity or clinically sensitive identity: " + ", ".join(matched))
    if risk in {"moderate to high", "moderate high", "high", "very high", "critical"}:
        reasons.append(f"source risk profile requires specialist review: {risk}")
    return reasons


def run(snapshot_path: Path, output: Path) -> dict[str, Any]:
    snapshot = load(snapshot_path)
    staging = snapshot["stagingDocuments"]
    if len(staging) != 870 or len(snapshot["governedExercises"]) != 264:
        raise ValueError("review snapshot count mismatch")
    duplicates = duplicate_pairs(staging)
    decisions: list[dict[str, Any]] = []
    projections: list[dict[str, Any]] = []
    excluded: list[dict[str, Any]] = []
    citations: list[dict[str, Any]] = []
    updates: list[dict[str, Any]] = []
    buckets = {name: [] for name in DECISIONS}
    for document in sorted(staging, key=lambda value: value["documentId"]):
        document_id = document["documentId"]
        fields = document["fields"]
        exercise = fields.get("exercise") if isinstance(fields.get("exercise"), dict) else {}
        source_id = fields.get("originalOntologySourceId") or exercise.get("sourceRecordId")
        original_checksum = fields.get("contentChecksum")
        reasons: list[str]
        duplicate_target = None
        outstanding: list[str] = []
        approved = None
        if not source_id or not original_checksum or not exercise.get("displayName") or not exercise.get("proposedCanonicalStableId"):
            decision, reasons = "REJECT_MALFORMED", ["missing required staging identity, checksum, name, or proposed stable ID"]
        elif document_id in duplicates:
            decision = "DEFER_DUPLICATE"
            duplicate_target = duplicates[document_id]["duplicateTarget"]
            reasons = duplicates[document_id]["reasons"] + [duplicates[document_id]["humanDecisionRequired"]]
            outstanding = [duplicates[document_id]["humanDecisionRequired"]]
        elif specialist_reasons(exercise):
            decision, reasons = "DEFER_SPECIALIST", specialist_reasons(exercise)
            outstanding = ["Qualified exercise, clinical, or biomechanics reviewer must validate identity, technique, and conservative safety presentation."]
        else:
            decision = "APPROVE_CORE"
            reasons = ["distinct legitimate exercise identity", "core taxonomy is internally usable", "unsupported advanced intelligence excluded", "conservative general-purpose instructions and safety projection applied"]
            approved = projection(exercise)
            projections.append(approved)
            excluded.append({"stagingDocumentId": document_id, "excludedFieldPaths": EXCLUDED_ADVANCED, "reason": "No record-level supporting citations; retained only in staging provenance."})
        projection_checksum = sha256(approved) if approved else None
        lifecycle = "CLASSIFIED → REVIEWED → APPROVED" if approved else "CLASSIFIED → REVIEWED"
        record = {
            "stagingDocumentId": document_id, "sourceOntologyId": source_id, "decision": decision,
            "reasons": reasons, "reviewerType": REVIEWER, "reviewedSourceChecksum": original_checksum,
            "approvedProjectionChecksum": projection_checksum, "duplicateTarget": duplicate_target,
            "excludedFieldPaths": EXCLUDED_ADVANCED if approved else [], "outstandingSpecialistRequirements": outstanding,
            "lifecycleTransition": lifecycle,
        }
        decisions.append(record)
        buckets[decision].append(record)
        update_fields = {
            "editorialState": "APPROVED" if approved else "REVIEWED",
            "reviewDecision": decision,
            "reviewReasons": reasons,
            "reviewerType": REVIEWER,
            "reviewedSourceChecksum": original_checksum,
            "approvedPublishableProjection": approved,
            "approvedProjectionChecksum": projection_checksum,
            "excludedFieldPaths": EXCLUDED_ADVANCED if approved else [],
            "duplicateTarget": duplicate_target,
            "outstandingSpecialistRequirements": outstanding,
            "reviewSchemaVersion": REVIEW_SCHEMA,
        }
        updates.append({"documentId": document_id, "expectedOriginalContentChecksum": original_checksum, "fields": update_fields, "updateChecksum": digest(update_fields)})
    if len(decisions) != 870 or set(item["decision"] for item in decisions) - DECISIONS:
        raise ValueError("not every staging record received one valid decision")
    resolve_alias_collisions(projections)
    resolved_by_id = {item["exerciseId"]: item for item in projections}
    decision_by_document = {item["stagingDocumentId"]: item for item in decisions}
    for update in updates:
        approved = update["fields"].get("approvedPublishableProjection")
        if approved:
            resolved = resolved_by_id[approved["exerciseId"]]
            checksum = sha256(resolved)
            update["fields"]["approvedPublishableProjection"] = resolved
            update["fields"]["approvedProjectionChecksum"] = checksum
            decision_by_document[update["documentId"]]["approvedProjectionChecksum"] = checksum
        update["updateChecksum"] = digest(update["fields"])
    governed = [document["fields"] for document in snapshot["governedExercises"]]
    candidate_exercises = sorted(governed + projections, key=lambda value: value["exerciseId"])
    errors = validate_exercises(candidate_exercises, {item["exerciseId"] for item in governed})
    if errors:
        raise ValueError("candidate schema validation failed: " + "; ".join(errors[:30]))
    candidate = {
        "baseReleaseId": snapshot["currentPointer"]["releaseId"], "schemaVersion": 1,
        "catalogueVersion": "2026.08.36-editorial-candidate", "exerciseCount": len(candidate_exercises),
        "exercises": candidate_exercises, "published": False,
    }
    candidate["payloadChecksum"] = sha256(candidate_exercises)
    update_core = {
        "project": "hv1-platform", "database": "(default)", "collection": "staging_exercises",
        "recordCount": len(updates), "documentIds": [item["documentId"] for item in updates],
        "perRecordChecksums": {item["documentId"]: item["updateChecksum"] for item in updates},
        "reviewSchemaVersion": REVIEW_SCHEMA, "publishedStatePresent": False,
        "releaseOrPointerPathsPresent": False,
    }
    update_manifest = {**update_core, "updatePayloadChecksum": digest(updates)}
    counts = {decision: len(buckets[decision]) for decision in sorted(DECISIONS)}
    quality = {
        "reviewed": len(decisions), "counts": counts, "advancedFieldsExcludedRecords": len(excluded),
        "excludedFieldOccurrences": sum(len(item["excludedFieldPaths"]) for item in excluded),
        "citationsVerified": 0, "approvedAdditions": len(projections), "candidateTotal": len(candidate_exercises),
        "uniqueIds": len({item["exerciseId"] for item in candidate_exercises}),
        "existingGovernedIdsPreserved": all(item in candidate_exercises for item in governed),
        "candidateSchemaValid": True,
    }
    output.mkdir(parents=True, exist_ok=True)
    artifact_map = {
        "editorial-decisions.json": decisions,
        "approved-core.json": buckets["APPROVE_CORE"], "approved-full.json": buckets["APPROVE_FULL"],
        "deferred-duplicates.json": buckets["DEFER_DUPLICATE"], "deferred-specialist.json": buckets["DEFER_SPECIALIST"],
        "rejected-malformed.json": buckets["REJECT_MALFORMED"], "rejected-unsafe.json": buckets["REJECT_UNSAFE"],
        "rejected-not-exercise.json": buckets["REJECT_NOT_EXERCISE"], "excluded-advanced-fields.json": excluded,
        "citation-verification.json": citations, "approved-publishable-projections.json": projections,
        "staging-editorial-update.json": updates, "staging-editorial-update-manifest.json": update_manifest,
        "approved-candidate-release.json": candidate,
        "approved-candidate-manifest.json": {"exerciseCount": len(candidate_exercises), "approvedAdditions": len(projections), "payloadChecksum": candidate["payloadChecksum"], "candidateChecksum": digest(candidate), "published": False},
        "quality-report.json": quality,
    }
    for filename, value in artifact_map.items():
        write(output / filename, value)
    follow_up = "# Human follow-up\n\nPublication is not authorized. Resolve every deferred duplicate and specialist item before publication readiness.\n\n"
    follow_up += f"- Duplicate decisions: {counts['DEFER_DUPLICATE']}\n- Specialist decisions: {counts['DEFER_SPECIALIST']}\n"
    (output / "human-follow-up.md").write_text(follow_up, encoding="utf-8")
    return quality


def compare(left: Path, right: Path) -> dict[str, Any]:
    names = sorted(path.name for path in left.iterdir() if path.is_file())
    if names != sorted(path.name for path in right.iterdir() if path.is_file()):
        raise ValueError("deterministic artifact sets differ")
    mismatches = [name for name in names if (left / name).read_bytes() != (right / name).read_bytes()]
    return {"byteEquivalent": not mismatches, "fileCount": len(names), "mismatches": mismatches}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--verify-determinism", action="store_true")
    args = parser.parse_args()
    if args.verify_determinism:
        first, second = args.output.parent / (args.output.name + "-a"), args.output.parent / (args.output.name + "-b")
        for folder in (first, second):
            if folder.exists(): shutil.rmtree(folder)
        run(args.snapshot, first); run(args.snapshot, second)
        result = compare(first, second)
        if not result["byteEquivalent"]: raise ValueError("review is not deterministic")
        preserved = {"capture_review.py", "review-production-snapshot.json", "review-input-manifest.json"}
        args.output.mkdir(parents=True, exist_ok=True)
        for path in list(args.output.iterdir()):
            if path.name not in preserved:
                path.unlink() if path.is_file() else shutil.rmtree(path)
        for path in first.iterdir(): shutil.copy2(path, args.output / path.name)
        write(args.output / "determinism-report.json", result)
        shutil.rmtree(first); shutil.rmtree(second)
        quality = load(args.output / "quality-report.json")
    else:
        quality = run(args.snapshot, args.output)
    print(canonical_json(quality))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
