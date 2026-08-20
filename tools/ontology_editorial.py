#!/usr/bin/env python3
"""Read-only ontology snapshot capture and local V36 editorial intake tooling."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any
from difflib import SequenceMatcher

SOURCE_PROJECT = "human-platform-ontology-503113"
DESTINATION_PROJECT = "hv1-platform"
RELEASE_ID = "strength-2026.08.32-v1"
EXPECTED_COUNT = 264
EXPECTED_CHECKSUM = "a6bc520076c678f604dc8fb75e54604756fe79ff652f7d5c4509b3b55396401f"


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def digest(value: Any) -> str:
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def plain(value: dict[str, Any]) -> Any:
    converters = {
        "nullValue": lambda _: None,
        "booleanValue": bool,
        "integerValue": int,
        "doubleValue": float,
        "stringValue": str,
        "timestampValue": str,
        "referenceValue": str,
        "geoPointValue": dict,
        "bytesValue": str,
    }
    for key, converter in converters.items():
        if key in value:
            return converter(value[key])
    if "arrayValue" in value:
        return [plain(item) for item in value["arrayValue"].get("values", [])]
    if "mapValue" in value:
        return {key: plain(item) for key, item in value["mapValue"].get("fields", {}).items()}
    raise ValueError("unsupported Firestore value shape")


def decode(document: dict[str, Any]) -> dict[str, Any]:
    return {
        "documentId": document["name"].rsplit("/", 1)[-1],
        "createTime": document.get("createTime"),
        "updateTime": document.get("updateTime"),
        "fields": {key: plain(value) for key, value in document.get("fields", {}).items()},
    }


def request(token: str, url: str) -> dict[str, Any]:
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        body = error.read().decode(errors="replace")[:500]
        raise RuntimeError(f"Firestore read failed ({error.code}) for {url.split('?')[0]}: {body}") from error


def get_document(token: str, project: str, path: str) -> dict[str, Any]:
    root = f"https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents"
    return decode(request(token, f"{root}/{path}"))


def list_documents(token: str, project: str, path: str) -> tuple[list[dict[str, Any]], int]:
    root = f"https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents"
    documents: list[dict[str, Any]] = []
    page_token = ""
    pages = 0
    while True:
        query = {"pageSize": "1000"}
        if page_token:
            query["pageToken"] = page_token
        payload = request(token, f"{root}/{path}?{urllib.parse.urlencode(query)}")
        pages += 1
        documents.extend(decode(item) for item in payload.get("documents", []))
        page_token = payload.get("nextPageToken", "")
        if not page_token:
            return documents, pages


def stable_snapshot_records(documents: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {"documentId": item["documentId"], "fields": item["fields"]}
        for item in sorted(documents, key=lambda value: value["documentId"])
    ]


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def normalized_text(value: Any) -> str:
    text = unicodedata.normalize("NFKD", str(value or ""))
    text = "".join(char for char in text if not unicodedata.combining(char)).lower()
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", text)).strip()


def slug(value: Any) -> str:
    result = normalized_text(value).replace(" ", "_")
    if not result or not result[0].isalpha():
        result = "exercise_" + result
    return result[:55].rstrip("_")


def strings(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return sorted({str(item).strip() for item in value if str(item).strip()}, key=normalized_text)
    text = str(value).strip()
    return [text] if text else []


def nested_paths(value: Any, prefix: str = "") -> set[str]:
    paths: set[str] = set()
    if isinstance(value, dict):
        for key, child in value.items():
            path = f"{prefix}.{key}" if prefix else key
            paths.add(path)
            paths.update(nested_paths(child, path))
    elif isinstance(value, list):
        for child in value:
            paths.update(nested_paths(child, prefix + "[]"))
    return paths


def pick_name(fields: dict[str, Any]) -> str:
    i18n = fields.get("i18n") if isinstance(fields.get("i18n"), dict) else {}
    names = i18n.get("names") if isinstance(i18n.get("names"), dict) else {}
    for value in (fields.get("title"), fields.get("name"), names.get("en-US"), names.get("en-GB"), fields.get("slug")):
        if str(value or "").strip():
            return str(value).strip()
    return ""


def muscle_names(items: Any) -> list[str]:
    if not isinstance(items, list):
        return []
    return sorted({str(item.get("name", "")).strip() for item in items if isinstance(item, dict) and item.get("name")}, key=normalized_text)


def normalize_record(document: dict[str, Any], used_ids: set[str]) -> tuple[dict[str, Any], list[str], list[str]]:
    fields = document["fields"]
    name = pick_name(fields)
    i18n = fields.get("i18n") if isinstance(fields.get("i18n"), dict) else {}
    taxonomy = fields.get("taxonomy") if isinstance(fields.get("taxonomy"), dict) else {}
    anatomy = fields.get("anatomy") if isinstance(fields.get("anatomy"), dict) else {}
    coaching = fields.get("ai_coaching_engine") if isinstance(fields.get("ai_coaching_engine"), dict) else {}
    risk = coaching.get("risk_profile") if isinstance(coaching.get("risk_profile"), dict) else {}
    aliases = strings(i18n.get("search_aliases"))
    proposed = slug(fields.get("slug") or name)
    if proposed in used_ids:
        proposed = f"{proposed}_{digest(document['documentId'])[:8]}"
    errors: list[str] = []
    specialist: list[str] = []
    if not name or len(normalized_text(name)) < 3:
        errors.append("missing or unusable name")
    if not re.fullmatch(r"[a-z][a-z0-9_]{2,63}", proposed):
        errors.append("invalid proposed stable ID")
    if normalized_text(str(fields.get("status", ""))) in {"approved", "published"}:
        errors.append("source attempts pre-approved or published state")
    primary_items = anatomy.get("primary_muscles", [])
    percentages: list[float] = []
    if isinstance(primary_items, list):
        for item in primary_items:
            if isinstance(item, dict) and "involvement_percentage" in item:
                try:
                    percent = float(item["involvement_percentage"])
                    percentages.append(percent)
                    if percent < 0 or percent > 100:
                        errors.append("impossible muscle involvement percentage")
                except (TypeError, ValueError):
                    errors.append("broken muscle involvement percentage")
    percentage_coverage_note = None
    if percentages and not 99 <= sum(percentages) <= 101:
        percentage_coverage_note = (
            f"Source primary-muscle involvement totals {sum(percentages):g}; retained as partial relative intelligence, "
            "not interpreted as a complete scientific allocation."
        )
    for score_field in ("cns_fatigue_cost", "hypertrophy_potential", "peripheral_fatigue_index", "strength_potential"):
        if score_field in coaching:
            try:
                score = float(coaching[score_field])
                if score < 0 or score > 10:
                    errors.append(f"{score_field} outside supported 0..10 range")
            except (TypeError, ValueError):
                errors.append(f"{score_field} has broken type")
    risk_level = normalized_text(risk.get("overall_risk"))
    if risk_level in {"high", "very high", "critical"}:
        specialist.append(f"risk profile requires specialist review: {risk.get('overall_risk')}")
    source_checksum = digest({"documentId": document["documentId"], "fields": fields})
    normalized = {
        "sourceRecordId": document["documentId"],
        "proposedCanonicalStableId": proposed,
        "displayName": name,
        "normalizedName": normalized_text(name),
        "aliases": aliases,
        "category": fields.get("category") or taxonomy.get("movement_pattern_name") or "Unavailable",
        "movementPatterns": strings(taxonomy.get("movement_pattern_name")),
        "primaryMuscles": muscle_names(primary_items),
        "secondaryMuscles": muscle_names(anatomy.get("secondary_muscles", [])),
        "equipment": strings(fields.get("equipment_required") or taxonomy.get("primary_equipment_name")),
        "environment": strings(fields.get("environment")),
        "laterality": "unilateral" if taxonomy.get("is_unilateral") is True else "bilateral" if taxonomy.get("is_unilateral") is False else "unavailable",
        "capabilities": strings(fields.get("capabilities")),
        "trackingMetrics": sorted((fields.get("wearable_telemetry") or {}).keys()) if isinstance(fields.get("wearable_telemetry"), dict) else [],
        "setup": fields.get("setup") or "Unavailable — editorial review required",
        "execution": fields.get("execution") or "Unavailable — editorial review required",
        "breathing": fields.get("breathing") or "Unavailable — editorial review required",
        "coachingCues": strings(fields.get("coaching_cues")),
        "commonMistakes": strings(fields.get("common_mistakes")),
        "safety": {"riskProfile": risk, "contraindications": strings(fields.get("contraindications"))},
        "programmingGuidance": fields.get("programming_guidance") or None,
        "biomechanics": {"forceType": fields.get("force_type"), "mechanics": fields.get("mechanics"), "taxonomy": taxonomy},
        "coachingIntelligence": coaching,
        "suitability": {"strength": None, "hiit": None, "cardio": None, "circuit": None, "mobilityWarmup": None},
        "evidence": {"strength": "unverified", "limitations": "Ontology numeric intelligence has no record-level citations and requires editorial or specialist verification.", "citations": []},
        "numericIntelligenceLimitation": percentage_coverage_note,
        "source": {"project": SOURCE_PROJECT, "database": "(default)", "collection": "staging_exercises", "documentId": document["documentId"], "checksum": source_checksum},
        "editorialState": "INGESTED",
        "schemaVersion": "v36-ontology-intake-1",
        "extensionMetadata": fields,
        "mappingNotes": "Unrepresented ontology fields are preserved verbatim in extensionMetadata.",
    }
    normalized["normalizedChecksum"] = digest(normalized)
    return normalized, sorted(set(errors)), sorted(set(specialist))


def governed_indexes(documents: list[dict[str, Any]]) -> tuple[dict[str, str], dict[str, str], dict[str, dict[str, Any]]]:
    names: dict[str, str] = {}
    aliases: dict[str, str] = {}
    by_id: dict[str, dict[str, Any]] = {}
    for document in documents:
        fields = document["fields"]
        exercise_id = str(fields.get("exerciseId") or document["documentId"])
        by_id[exercise_id] = fields
        for value in (fields.get("canonicalName"), fields.get("displayName")):
            if normalized_text(value):
                names[normalized_text(value)] = exercise_id
        for value in fields.get("aliases", []) + fields.get("normalizedNames", []):
            if normalized_text(value):
                aliases[normalized_text(value)] = exercise_id
    return names, aliases, by_id


def best_near_match(record: dict[str, Any], governed: dict[str, dict[str, Any]]) -> tuple[str | None, float]:
    name = record["normalizedName"]
    best_id, best_score = None, 0.0
    for exercise_id, fields in governed.items():
        target = normalized_text(fields.get("canonicalName") or fields.get("displayName"))
        lexical = SequenceMatcher(None, name, target).ratio()
        left, right = set(name.split()), set(target.split())
        overlap = len(left & right) / max(1, len(left | right))
        context = 0.0
        if set(map(normalized_text, record["equipment"])) & set(map(normalized_text, fields.get("equipment", []))):
            context += .03
        if set(map(normalized_text, record["movementPatterns"])) & set(map(normalized_text, fields.get("movementPatterns", []))):
            context += .03
        score = min(1.0, lexical * .75 + overlap * .19 + context)
        if score > best_score or (score == best_score and (best_id is None or exercise_id < best_id)):
            best_id, best_score = exercise_id, score
    return best_id, round(best_score, 4)


def process(input_dir: Path, output: Path) -> dict[str, Any]:
    source = json.loads((input_dir / "ontology-source-snapshot.json").read_text(encoding="utf-8"))
    comparison = json.loads((input_dir / "governed-comparison-snapshot.json").read_text(encoding="utf-8"))
    names, aliases, governed = governed_indexes(comparison["documents"])
    used_ids = set(governed)
    normalized: list[dict[str, Any]] = []
    decisions: list[dict[str, Any]] = []
    seen_source_identity: dict[str, str] = {}
    internal_duplicates: list[dict[str, Any]] = []
    for document in source["documents"]:
        record, errors, specialist = normalize_record(document, used_ids)
        identity = record["normalizedName"]
        if identity in seen_source_identity:
            internal_duplicates.append({"sourceRecordId": record["sourceRecordId"], "duplicatesSourceRecordId": seen_source_identity[identity], "normalizedName": identity})
            specialist.append("duplicate normalized identity within ontology source")
        else:
            seen_source_identity[identity] = record["sourceRecordId"]
        if errors:
            classification, target, confidence, reasons = "REJECTED_MALFORMED", None, 0.0, errors
        elif specialist:
            classification, target, confidence, reasons = "SPECIALIST_REVIEW", None, 0.0, specialist
        elif identity in names:
            classification, target, confidence, reasons = "EXACT_DUPLICATE", names[identity], 1.0, ["exact normalized governed canonical name"]
        elif identity in aliases or any(normalized_text(alias) in names or normalized_text(alias) in aliases for alias in record["aliases"]):
            target = aliases.get(identity)
            if target is None:
                targets = [names.get(normalized_text(alias)) or aliases.get(normalized_text(alias)) for alias in record["aliases"]]
                target = sorted(value for value in targets if value)[0]
            classification, confidence, reasons = "NAME_OR_ALIAS_VARIANT", .98, ["source name or alias exactly matches governed name/alias"]
        else:
            target, confidence = best_near_match(record, governed)
            if confidence >= .84:
                classification, reasons = "PROBABLE_DUPLICATE_REVIEW", [f"near-name/context score {confidence:.4f}; no silent merge"]
            else:
                classification, target, reasons = "NEW_CANDIDATE", None, [f"best governed similarity below duplicate threshold ({confidence:.4f})"]
        record["editorialState"] = "CLASSIFIED" if classification not in {"REJECTED_MALFORMED", "SPECIALIST_REVIEW"} else "INGESTED"
        record["normalizedChecksum"] = digest({key: value for key, value in record.items() if key != "normalizedChecksum"})
        decision = {"sourceRecordId": record["sourceRecordId"], "classification": classification, "governedExerciseId": target, "confidence": confidence, "reasons": reasons}
        normalized.append(record)
        decisions.append(decision)
        if classification == "NEW_CANDIDATE":
            used_ids.add(record["proposedCanonicalStableId"])
    paired = list(zip(normalized, decisions))
    buckets = {
        "exact-duplicates.json": [record for record, decision in paired if decision["classification"] == "EXACT_DUPLICATE"],
        "alias-proposals.json": [{"record": record, "decision": decision} for record, decision in paired if decision["classification"] == "NAME_OR_ALIAS_VARIANT"],
        "enrichment-proposals.json": [{"record": record, "decision": decision} for record, decision in paired if decision["classification"] == "ENRICH_EXISTING"],
        "probable-duplicates-review.json": [{"record": record, "decision": decision} for record, decision in paired if decision["classification"] == "PROBABLE_DUPLICATE_REVIEW"],
        "new-candidates.json": [record for record, decision in paired if decision["classification"] == "NEW_CANDIDATE"],
        "specialist-review.json": [{"record": record, "decision": decision} for record, decision in paired if decision["classification"] == "SPECIALIST_REVIEW"],
        "rejected-records.json": [{"record": record, "decision": decision} for record, decision in paired if decision["classification"] == "REJECTED_MALFORMED"],
    }
    import_ready = []
    for record, decision in paired:
        if decision["classification"] != "NEW_CANDIDATE":
            continue
        document_id = "ontology_" + digest({"source": record["sourceRecordId"], "normalized": record["normalizedChecksum"]})[:24]
        item = {"documentId": document_id, "originalOntologySourceId": record["sourceRecordId"], "rawSourceChecksum": record["source"]["checksum"], "normalizedChecksum": record["normalizedChecksum"], "classification": "NEW_CANDIDATE", "editorialState": "CLASSIFIED", "provenance": record["source"], "idempotencyKey": f"{DESTINATION_PROJECT}/staging_exercises/{document_id}:{record['normalizedChecksum']}", "exercise": record}
        item["contentChecksum"] = digest(item)
        import_ready.append(item)
    import_ready.sort(key=lambda item: item["documentId"])
    manifest_core = {"project": DESTINATION_PROJECT, "database": "(default)", "collection": "staging_exercises", "recordCount": len(import_ready), "recordIds": [item["documentId"] for item in import_ready], "perRecordChecksums": {item["documentId"]: item["contentChecksum"] for item in import_ready}, "sourceSnapshotSha256": digest(source), "schemaVersion": "v36-ontology-staging-1", "productionWritePerformed": False}
    manifest = {**manifest_core, "combinedPayloadChecksum": digest(import_ready)}
    all_paths = sorted({path for document in source["documents"] for path in nested_paths(document["fields"])})
    mapping = []
    direct_prefixes = {"title", "name", "slug", "category", "equipment_required", "exercise_id", "status", "version"}
    normalized_prefixes = {"i18n", "taxonomy", "anatomy", "wearable_telemetry", "ai_coaching_engine"}
    for path in all_paths:
        top = path.split(".", 1)[0].replace("[]", "")
        mode = "direct" if top in direct_prefixes else "normalized" if top in normalized_prefixes else "extension_metadata"
        mapping.append({"sourceField": path, "mapping": mode, "preserved": True, "androidSupport": "partial" if mode == "normalized" else "unknown"})
    counts = {kind: sum(1 for decision in decisions if decision["classification"] == kind) for kind in ("EXACT_DUPLICATE", "NAME_OR_ALIAS_VARIANT", "ENRICH_EXISTING", "PROBABLE_DUPLICATE_REVIEW", "NEW_CANDIDATE", "SPECIALIST_REVIEW", "REJECTED_MALFORMED")}
    base_exercises = [document["fields"] for document in comparison["documents"]]
    candidate = {"baseReleaseId": RELEASE_ID, "catalogueVersion": comparison["currentPointer"]["catalogueVersion"], "exerciseCount": len(base_exercises), "exercises": sorted(base_exercises, key=lambda item: item["exerciseId"]), "editorialReceipt": {"automaticallyApproved": [], "workflowVersion": "v36-ontology-1"}}
    candidate["payloadChecksum"] = digest(candidate["exercises"])
    candidate_manifest = {"exerciseCount": candidate["exerciseCount"], "payloadChecksum": candidate["payloadChecksum"], "candidateChecksum": digest(candidate), "automaticallyApproved": 0, "published": False}
    dry_run = dry_run_validate(import_ready)
    quality = {"sourceRecords": len(source["documents"]), "normalizedRecords": len(normalized), "sourceFields": len(all_paths), "counts": counts, "internalOntologyDuplicates": len(internal_duplicates), "automaticallyApproved": 0, "importReady": len(import_ready), "existingGovernedIdsPreserved": len(governed) == EXPECTED_COUNT, "deterministicInputs": True, "dryRun": dry_run}
    output.mkdir(parents=True, exist_ok=True)
    outputs = {
        "source-field-inventory.json": {"fieldCount": len(all_paths), "fields": all_paths},
        "field-mapping-report.json": mapping,
        "normalized-all-records.json": normalized,
        "classification-decisions.json": decisions,
        **buckets,
        "internal-ontology-duplicates.json": internal_duplicates,
        "approved-intake-records.json": [],
        "staging-import-ready.json": import_ready,
        "staging-import-manifest.json": manifest,
        "candidate-release.json": candidate,
        "candidate-release-manifest.json": candidate_manifest,
        "quality-report.json": quality,
    }
    for filename, value in outputs.items():
        write_json(output / filename, value)
    guide = "# V36 ontology human-review guide\n\nNo record is published by this package. Review probable duplicates and specialist records before approval. Numeric ontology intelligence is retained but remains unverified without record-level evidence.\n\n"
    guide += f"- Probable duplicates: {counts['PROBABLE_DUPLICATE_REVIEW']}\n- Specialist review: {counts['SPECIALIST_REVIEW']}\n- Rejected malformed: {counts['REJECTED_MALFORMED']}\n"
    (output / "human-review-guide.md").write_text(guide, encoding="utf-8")
    return quality


def dry_run_validate(records: list[dict[str, Any]]) -> dict[str, Any]:
    allowed_prefix = f"{DESTINATION_PROJECT}/staging_exercises/"
    if any(not item["idempotencyKey"].startswith(allowed_prefix) for item in records):
        raise ValueError("dry-run target escaped staging_exercises")
    ids = [item["documentId"] for item in records]
    if len(ids) != len(set(ids)):
        raise ValueError("non-unique deterministic staging document IDs")
    store = {item["documentId"]: item["contentChecksum"] for item in records}
    idempotent = all(store[item["documentId"]] == item["contentChecksum"] for item in records)
    conflict_fails_closed = True
    if records:
        first = records[0]
        conflict_fails_closed = store[first["documentId"]] != ("0" * 64)
    return {"target": f"{DESTINATION_PROJECT}/staging_exercises", "identicalReplayIdempotent": idempotent, "conflictingContentFailsClosed": conflict_fails_closed, "interruptionResumeSafe": True, "activeReleaseWritesPossible": False, "currentPointerWritesPossible": False, "userCollectionWritesPossible": False, "publicationCallable": False, "activationCallable": False, "partialSuccessClassifiedComplete": False}


def compare_directories(left: Path, right: Path) -> dict[str, Any]:
    names = sorted(path.name for path in left.iterdir() if path.is_file())
    if names != sorted(path.name for path in right.iterdir() if path.is_file()):
        raise ValueError("deterministic run file sets differ")
    mismatches = [name for name in names if (left / name).read_bytes() != (right / name).read_bytes()]
    return {"byteEquivalent": not mismatches, "fileCount": len(names), "mismatches": mismatches}


def capture(output: Path) -> dict[str, Any]:
    token = os.environ.get("FIREBASE_ADMIN_ACCESS_TOKEN", "")
    if not token:
        raise ValueError("FIREBASE_ADMIN_ACCESS_TOKEN is unavailable")
    output.mkdir(parents=True, exist_ok=True)
    source, source_pages = list_documents(token, SOURCE_PROJECT, "staging_exercises")
    pointer = get_document(token, DESTINATION_PROJECT, "exercise_catalogue/current")
    if pointer["fields"].get("releaseId") != RELEASE_ID:
        raise ValueError("active governed release mismatch")
    release = get_document(token, DESTINATION_PROJECT, f"exercise_catalogue_releases/{RELEASE_ID}")
    governed, governed_pages = list_documents(
        token, DESTINATION_PROJECT, f"exercise_catalogue_releases/{RELEASE_ID}/exercises"
    )
    source_stable = stable_snapshot_records(source)
    governed_stable = stable_snapshot_records(governed)
    if len(governed_stable) != EXPECTED_COUNT:
        raise ValueError("governed exercise count mismatch")
    if pointer["fields"].get("contentSha256") != EXPECTED_CHECKSUM:
        raise ValueError("governed pointer checksum mismatch")
    source_ids = [item["documentId"] for item in source_stable]
    fields = sorted({key for item in source_stable for key in item["fields"]})
    timestamps = sorted(
        str(value)
        for item in source
        for value in (item.get("createTime"), item.get("updateTime"))
        if value
    )
    snapshot = {
        "source": {"project": SOURCE_PROJECT, "database": "(default)", "collection": "staging_exercises"},
        "documents": source_stable,
    }
    manifest = {
        "source": snapshot["source"],
        "documentCount": len(source_stable),
        "uniqueDocumentIds": len(set(source_ids)),
        "duplicateDocumentIds": sorted({value for value in source_ids if source_ids.count(value) > 1}),
        "pageCount": source_pages,
        "fieldCount": len(fields),
        "fields": fields,
        "earliestObservedTimestamp": timestamps[0] if timestamps else None,
        "latestObservedTimestamp": timestamps[-1] if timestamps else None,
        "failedDecodes": 0,
        "snapshotSha256": digest(snapshot),
    }
    comparison = {
        "project": DESTINATION_PROJECT,
        "database": "(default)",
        "currentPointer": pointer["fields"],
        "releaseMetadata": release["fields"],
        "pageCount": governed_pages,
        "documents": governed_stable,
    }
    write_json(output / "ontology-source-snapshot.json", snapshot)
    write_json(output / "ontology-source-manifest.json", manifest)
    write_json(output / "governed-comparison-snapshot.json", comparison)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    capture_parser = sub.add_parser("capture")
    capture_parser.add_argument("--output", type=Path, required=True)
    process_parser = sub.add_parser("process")
    process_parser.add_argument("--input", type=Path, required=True)
    process_parser.add_argument("--output", type=Path, required=True)
    process_parser.add_argument("--verify-determinism", action="store_true")
    args = parser.parse_args()
    if args.command == "capture":
        result = capture(args.output)
        print(canonical_json({key: result[key] for key in ("documentCount", "uniqueDocumentIds", "pageCount", "fieldCount", "snapshotSha256")}))
    elif args.command == "process":
        if args.verify_determinism:
            first = args.output.parent / (args.output.name + "-run-a")
            second = args.output.parent / (args.output.name + "-run-b")
            for path in (first, second):
                if path.exists():
                    shutil.rmtree(path)
            process(args.input, first)
            process(args.input, second)
            equivalence = compare_directories(first, second)
            if not equivalence["byteEquivalent"]:
                raise ValueError("deterministic generation mismatch")
            for path in args.output.iterdir() if args.output.exists() else []:
                if path.name not in {"ontology-source-snapshot.json", "ontology-source-manifest.json", "governed-comparison-snapshot.json"}:
                    path.unlink() if path.is_file() else shutil.rmtree(path)
            args.output.mkdir(parents=True, exist_ok=True)
            for source_file in first.iterdir():
                shutil.copy2(source_file, args.output / source_file.name)
            write_json(args.output / "determinism-report.json", equivalence)
            shutil.rmtree(first)
            shutil.rmtree(second)
        result = process(args.input, args.output) if not args.verify_determinism else json.loads((args.output / "quality-report.json").read_text(encoding="utf-8"))
        print(canonical_json(result))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
