"""Deterministic Human V1 governed exercise catalogue publisher/validator.

Dry-run is the default. Writes are restricted to an explicit demo Firebase emulator.
This tool never reads or writes production Firebase and never handles user data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import urllib.request
import urllib.error
from pathlib import Path
from typing import Any, Iterable

SCHEMA_VERSION = 1
PUBLISHER_VERSION = "human-v1-governed-publisher/1.0"
ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]{2,63}$")
CAPABILITIES = {"repetitions", "load", "duration", "distance", "bodyweight", "assisted_load", "weighted_bodyweight", "rpe", "tempo"}
LATERALITIES = {"bilateral", "unilateral"}
EDITORIAL_STATES = {"approved", "deprecated"}


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256(value: Any) -> str:
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def normalized(value: str) -> str:
    return " ".join(re.sub(r"[^a-z0-9]+", " ", value.lower()).split())


def governed_exercise(source: dict[str, Any], now: str) -> dict[str, Any]:
    exercise_type = source["type"]
    equipment = list(source.get("equipment", []))
    category = source["category"]
    is_cardio = exercise_type in {"cardio", "conditioning"} or category == "Cardio"
    is_bodyweight = bool(source.get("bodyweight"))
    return {
        "exerciseId": source["id"], "schemaVersion": SCHEMA_VERSION,
        "canonicalName": source["name"], "displayName": source["name"],
        "aliases": sorted(source.get("aliases", []), key=str.casefold),
        "summary": f"A {source.get('movementPattern') or category.lower()} exercise using {', '.join(equipment) or 'no equipment'}.",
        "category": category, "exerciseType": exercise_type,
        "movementPatterns": [source.get("movementPattern") or category.lower()],
        "primaryMuscles": source.get("primaryMuscles", []), "secondaryMuscles": source.get("secondaryMuscles", []),
        "equipment": equipment, "laterality": source["laterality"],
        "trackingCapabilities": sorted(source["capabilities"]), "timedCompatible": "duration" in source["capabilities"],
        "recommendedForStrength": exercise_type != "cardio", "recommendedForHiit": is_cardio or is_bodyweight,
        "cardioSuitable": is_cardio, "circuitSuitable": is_cardio or is_bodyweight,
        "timedIntervalSuitable": "duration" in source["capabilities"],
        "gymSuitable": any(item not in {"bodyweight", "none"} for item in equipment),
        "homeSuitable": is_bodyweight or not equipment or all(item in {"bodyweight", "dumbbell", "resistance band", "kettlebell", "bench"} for item in equipment),
        "setupInstructions": source.get("setup", ""), "executionInstructions": source.get("steps", []),
        "breathingGuidance": source.get("breathing", ""), "techniqueCues": source.get("cues", []),
        "commonMistakes": source.get("mistakes", []), "safetyGuidance": source.get("safety", ""),
        "contraindicationCautions": [],
        "progressionIds": [source["progressionId"]] if source.get("progressionId") else [],
        "regressionIds": [source["regressionId"]] if source.get("regressionId") else [],
        "relatedExerciseIds": source.get("relatedIds", []),
        "deprecated": not source.get("active", True), "replacementExerciseId": source.get("replacementId"),
        "evidenceSummaryState": "editorial_baseline", "evidenceReferenceIds": [],
        "localizationReady": True, "defaultLocale": "en-GB", "availableLocales": ["en-GB"],
        "contentRevision": 1, "editorialStatus": "deprecated" if not source.get("active", True) else "approved",
        "createdAt": now, "updatedAt": now,
        "normalizedNames": sorted({normalized(source["name"]), *(normalized(alias) for alias in source.get("aliases", []))}),
        "suppressedAliases": [],
    }


def resolve_alias_collisions(items: list[dict[str, Any]]) -> None:
    """Prefer canonical names and first stable alias ownership, retaining an audit trail."""
    owners = {normalized(item["canonicalName"]): item["exerciseId"] for item in items}
    for item in sorted(items, key=lambda value: value["exerciseId"]):
        accepted: list[str] = []
        suppressed: list[str] = []
        for alias in item["aliases"]:
            key = normalized(alias)
            owner = owners.setdefault(key, item["exerciseId"])
            (accepted if owner == item["exerciseId"] else suppressed).append(alias)
        item["aliases"] = accepted
        item["suppressedAliases"] = suppressed
        item["normalizedNames"] = sorted({normalized(item["canonicalName"]), *(normalized(alias) for alias in accepted)})


def validate_exercises(items: list[dict[str, Any]], required_ids: set[str] | None = None) -> list[str]:
    errors: list[str] = []
    ids = [item.get("exerciseId") for item in items]
    id_set = set(ids)
    if len(id_set) != len(ids): errors.append("duplicate exercise IDs")
    if required_ids is not None and not required_ids.issubset(id_set): errors.append("original governed IDs missing")
    names: dict[str, str] = {}
    for item in items:
        item_id = item.get("exerciseId", "")
        if not ID_PATTERN.fullmatch(item_id): errors.append(f"{item_id or '<missing>'}: invalid identity")
        if item.get("schemaVersion") != SCHEMA_VERSION: errors.append(f"{item_id}: unsupported schema")
        for field in ("canonicalName", "displayName", "summary", "category", "setupInstructions", "breathingGuidance", "safetyGuidance"):
            if not item.get(field): errors.append(f"{item_id}: missing {field}")
        if item.get("laterality") not in LATERALITIES: errors.append(f"{item_id}: invalid laterality")
        capabilities = set(item.get("trackingCapabilities", []))
        if not capabilities or not capabilities <= CAPABILITIES: errors.append(f"{item_id}: invalid capabilities")
        if ({"assisted_load", "weighted_bodyweight"} & capabilities) and "bodyweight" not in capabilities:
            errors.append(f"{item_id}: bodyweight load capability requires bodyweight")
        if item.get("editorialStatus") not in EDITORIAL_STATES: errors.append(f"{item_id}: invalid editorial status")
        terms = [item.get("canonicalName", ""), *item.get("aliases", [])]
        for term in terms:
            key = normalized(term)
            if not key: errors.append(f"{item_id}: blank name or alias"); continue
            previous = names.setdefault(key, item_id)
            if previous != item_id: errors.append(f"{item_id}: name/alias collision with {previous}")
        for field in ("relatedExerciseIds", "progressionIds", "regressionIds"):
            refs = item.get(field, [])
            if len(refs) != len(set(refs)): errors.append(f"{item_id}: duplicate {field}")
            if item_id in refs: errors.append(f"{item_id}: self reference in {field}")
            if any(ref not in id_set for ref in refs): errors.append(f"{item_id}: broken {field}")
        replacement = item.get("replacementExerciseId")
        if replacement == item_id or (replacement and replacement not in id_set): errors.append(f"{item_id}: invalid replacement")
    return sorted(set(errors))


def build_release(source: dict[str, Any], release_id: str, published_at: str) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    items = [governed_exercise(item, published_at) for item in source["exercises"]]
    items.sort(key=lambda item: item["exerciseId"])
    resolve_alias_collisions(items)
    errors = validate_exercises(items, {item["id"] for item in source["exercises"]})
    if errors: raise ValueError("; ".join(errors))
    checksum = sha256(items)
    release = {
        "releaseId": release_id, "schemaVersion": SCHEMA_VERSION,
        "catalogueVersion": source["catalogueVersion"], "exerciseCount": len(items), "contentSha256": checksum,
        "publishedAt": published_at, "minimumStrengthVersionCode": 33, "minimumHiitVersionCode": 1,
        "status": "published", "channel": "production", "previousReleaseId": None,
        "publisherToolVersion": PUBLISHER_VERSION, "validationStatus": "validated",
        "editorialNotes": "Generated from the verified V32 bundled baseline.",
    }
    return release, items


def classify_staging(item: dict[str, Any], governed_ids: set[str], governed_terms: set[str]) -> str:
    item_id = str(item.get("id") or item.get("exerciseId") or "")
    if item_id in governed_ids: return "Already represented by a governed ID"
    if item.get("rejected") is True: return "Rejected"
    if not item_id or not item.get("name") or not item.get("category"): return "Missing required data"
    if normalized(str(item["name"])) in governed_terms: return "Probable duplicate"
    capabilities = set(item.get("capabilities", []))
    if not capabilities or not capabilities <= CAPABILITIES: return "Invalid capability combination"
    if item.get("taxonomySupported") is False: return "Unsupported taxonomy"
    if not item.get("safety"): return "Requires safety review"
    if not item.get("evidenceReviewed"): return "Requires scientific/editorial review"
    return "Ready to normalize"


def emulator_write(project: str, release: dict[str, Any], exercises: list[dict[str, Any]]) -> None:
    if not project.startswith("demo-"): raise ValueError("emulator publisher requires a demo- project")
    host = os.environ.get("FIRESTORE_EMULATOR_HOST")
    if not host or host not in {"127.0.0.1:8080", "localhost:8080"}: raise ValueError("explicit local Firestore emulator required")
    root = f"projects/{project}/databases/(default)/documents"
    release_id = release["releaseId"]
    release_url = f"http://{host}/v1/{root}/exercise_catalogue_releases/{release_id}"
    owner_headers = {"Authorization": "Bearer owner"}
    try:
        urllib.request.urlopen(urllib.request.Request(release_url, headers=owner_headers), timeout=10).read()
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
    else:
        raise ValueError(f"immutable release already exists: {release_id}")
    def firestore_value(value: Any) -> dict[str, Any]:
        if value is None: return {"nullValue": None}
        if isinstance(value, bool): return {"booleanValue": value}
        if isinstance(value, int): return {"integerValue": str(value)}
        if isinstance(value, float): return {"doubleValue": value}
        if isinstance(value, str): return {"stringValue": value}
        if isinstance(value, list): return {"arrayValue": {"values": [firestore_value(item) for item in value]}}
        if isinstance(value, dict): return {"mapValue": {"fields": {key: firestore_value(item) for key, item in value.items()}}}
        raise TypeError(f"unsupported Firestore value: {type(value).__name__}")
    def write(path: str, fields: dict[str, Any]) -> dict[str, Any]:
        return {"update": {"name": f"{root}/{path}", "fields": {key: firestore_value(value) for key, value in fields.items()}}}
    manifest = dict(release)
    writes = [write(f"exercise_catalogue_releases/{release_id}", release)]
    writes.extend(write(f"exercise_catalogue_releases/{release_id}/exercises/{item['exerciseId']}", item) for item in exercises)
    writes.append(write("exercise_catalogue/current", manifest))
    payload = {"writes": writes}
    request = urllib.request.Request(
        f"http://{host}/v1/{root}:commit",
        data=canonical_json(payload).encode(),
        method="POST",
        headers={"Content-Type": "application/json", **owner_headers},
    )
    response = json.loads(urllib.request.urlopen(request, timeout=10).read())
    if len(response.get("writeResults", [])) != len(writes):
        raise RuntimeError("emulator commit did not confirm every immutable document")


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=Path("app/src/main/assets/strength-exercise-catalogue.json"))
    parser.add_argument("--release-id", default="strength-2026.08.32-v1")
    parser.add_argument("--published-at", default="2026-08-14T00:00:00Z")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--project", default="demo-hv1-strength-local")
    parser.add_argument("--write-emulator", action="store_true")
    parser.add_argument("--staging", type=Path)
    args = parser.parse_args(argv)
    source = json.loads(args.source.read_text(encoding="utf-8"))
    release, exercises = build_release(source, args.release_id, args.published_at)
    report: dict[str, Any] = {"valid": True, "releaseId": release["releaseId"], "schemaVersion": SCHEMA_VERSION,
        "exerciseCount": len(exercises), "contentSha256": release["contentSha256"], "mode": "dry-run", "errors": []}
    if args.staging:
        staging = json.loads(args.staging.read_text(encoding="utf-8"))
        ids = {item["exerciseId"] for item in exercises}
        terms = {term for item in exercises for term in item["normalizedNames"]}
        report["staging"] = [{"candidateKey": hashlib.sha256(canonical_json(item).encode()).hexdigest()[:12], "classification": classify_staging(item, ids, terms)} for item in staging]
    if args.output:
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "manifest.json").write_text(canonical_json(release) + "\n", encoding="utf-8")
        (args.output / "exercises.json").write_text(canonical_json(exercises) + "\n", encoding="utf-8")
    if args.write_emulator:
        emulator_write(args.project, release, exercises); report["mode"] = "emulator-write"
    print(canonical_json(report))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
