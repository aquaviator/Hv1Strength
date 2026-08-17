"""Separated, fail-closed Human V1 governed catalogue release tool."""
from __future__ import annotations

import argparse, hashlib, json, os, re, urllib.error, urllib.parse, urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Protocol

SCHEMA_VERSION = 1
PUBLISHER_VERSION = "human-v1-governed-publisher/2.0"
PRODUCTION_PROJECT = "hv1-platform"
MAX_COMMIT_WRITES = 400
ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]{2,63}$")
CAPABILITIES = {"repetitions", "load", "duration", "distance", "bodyweight", "assisted_load", "weighted_bodyweight", "rpe", "tempo"}
LATERALITIES = {"bilateral", "unilateral"}
EDITORIAL_STATES = {"approved", "deprecated"}


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256(value: Any) -> str:
    return hashlib.sha256(canonical_json(value).encode()).hexdigest()


def normalized(value: str) -> str:
    return " ".join(re.sub(r"[^a-z0-9]+", " ", value.lower()).split())


def governed_exercise(source: dict[str, Any], now: str) -> dict[str, Any]:
    exercise_type, equipment, category = source["type"], list(source.get("equipment", [])), source["category"]
    is_cardio = exercise_type in {"cardio", "conditioning"} or category == "Cardio"
    is_bodyweight = bool(source.get("bodyweight"))
    return {
        "exerciseId": source["id"], "schemaVersion": SCHEMA_VERSION, "canonicalName": source["name"],
        "displayName": source["name"], "aliases": sorted(source.get("aliases", []), key=str.casefold),
        "summary": f"A {source.get('movementPattern') or category.lower()} exercise using {', '.join(equipment) or 'no equipment'}.",
        "category": category, "exerciseType": exercise_type,
        "movementPatterns": [source.get("movementPattern") or category.lower()],
        "primaryMuscles": source.get("primaryMuscles", []), "secondaryMuscles": source.get("secondaryMuscles", []),
        "equipment": equipment, "laterality": source["laterality"],
        "trackingCapabilities": sorted(source["capabilities"]), "timedCompatible": "duration" in source["capabilities"],
        "recommendedForStrength": exercise_type != "cardio", "recommendedForHiit": is_cardio or is_bodyweight,
        "cardioSuitable": is_cardio, "circuitSuitable": is_cardio or is_bodyweight,
        "timedIntervalSuitable": "duration" in source["capabilities"],
        "gymSuitable": any(x not in {"bodyweight", "none"} for x in equipment),
        "homeSuitable": is_bodyweight or not equipment or all(x in {"bodyweight", "dumbbell", "resistance band", "kettlebell", "bench"} for x in equipment),
        "setupInstructions": source.get("setup", ""), "executionInstructions": source.get("steps", []),
        "breathingGuidance": source.get("breathing", ""), "techniqueCues": source.get("cues", []),
        "commonMistakes": source.get("mistakes", []), "safetyGuidance": source.get("safety", ""),
        "contraindicationCautions": [], "progressionIds": [source["progressionId"]] if source.get("progressionId") else [],
        "regressionIds": [source["regressionId"]] if source.get("regressionId") else [],
        "relatedExerciseIds": source.get("relatedIds", []), "deprecated": not source.get("active", True),
        "replacementExerciseId": source.get("replacementId"), "evidenceSummaryState": "editorial_baseline",
        "evidenceReferenceIds": [], "localizationReady": True, "defaultLocale": "en-GB", "availableLocales": ["en-GB"],
        "contentRevision": 1, "editorialStatus": "deprecated" if not source.get("active", True) else "approved",
        "createdAt": now, "updatedAt": now,
        "normalizedNames": sorted({normalized(source["name"]), *(normalized(a) for a in source.get("aliases", []))}),
        "suppressedAliases": [],
    }


def resolve_alias_collisions(items: list[dict[str, Any]]) -> None:
    owners = {normalized(x["canonicalName"]): x["exerciseId"] for x in items}
    for item in sorted(items, key=lambda x: x["exerciseId"]):
        accepted, suppressed = [], []
        for alias in item["aliases"]:
            owner = owners.setdefault(normalized(alias), item["exerciseId"])
            (accepted if owner == item["exerciseId"] else suppressed).append(alias)
        item["aliases"], item["suppressedAliases"] = accepted, suppressed
        item["normalizedNames"] = sorted({normalized(item["canonicalName"]), *(normalized(a) for a in accepted)})


def validate_exercises(items: list[dict[str, Any]], required_ids: set[str] | None = None) -> list[str]:
    errors, ids, names = [], [x.get("exerciseId") for x in items], {}
    id_set = set(ids)
    if len(id_set) != len(ids): errors.append("duplicate exercise IDs")
    if required_ids is not None and not required_ids.issubset(id_set): errors.append("original governed IDs missing")
    for item in items:
        item_id = item.get("exerciseId", "")
        if not ID_PATTERN.fullmatch(item_id): errors.append(f"{item_id or '<missing>'}: invalid identity")
        if item.get("schemaVersion") != SCHEMA_VERSION: errors.append(f"{item_id}: unsupported schema")
        for field in ("canonicalName", "displayName", "summary", "category", "setupInstructions", "breathingGuidance", "safetyGuidance"):
            if not item.get(field): errors.append(f"{item_id}: missing {field}")
        if item.get("laterality") not in LATERALITIES: errors.append(f"{item_id}: invalid laterality")
        caps = set(item.get("trackingCapabilities", []))
        if not caps or not caps <= CAPABILITIES: errors.append(f"{item_id}: invalid capabilities")
        if ({"assisted_load", "weighted_bodyweight"} & caps) and "bodyweight" not in caps: errors.append(f"{item_id}: bodyweight load capability requires bodyweight")
        if item.get("editorialStatus") not in EDITORIAL_STATES: errors.append(f"{item_id}: invalid editorial status")
        for term in [item.get("canonicalName", ""), *item.get("aliases", [])]:
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
    items = sorted((governed_exercise(x, published_at) for x in source["exercises"]), key=lambda x: x["exerciseId"])
    resolve_alias_collisions(items)
    errors = validate_exercises(items, {x["id"] for x in source["exercises"]})
    if errors: raise ValueError("; ".join(errors))
    release = {"releaseId": release_id, "schemaVersion": SCHEMA_VERSION, "catalogueVersion": source["catalogueVersion"],
        "exerciseCount": len(items), "contentSha256": sha256(items), "publishedAt": published_at,
        "minimumStrengthVersionCode": 33, "minimumHiitVersionCode": 1, "status": "published", "channel": "production",
        "previousReleaseId": None, "publisherToolVersion": PUBLISHER_VERSION, "validationStatus": "validated",
        "editorialNotes": "Generated from the verified V32 bundled baseline."}
    return release, items


def classify_staging(item: dict[str, Any], governed_ids: set[str], governed_terms: set[str]) -> str:
    item_id = str(item.get("id") or item.get("exerciseId") or "")
    if item_id in governed_ids: return "Already represented by a governed ID"
    if item.get("rejected") is True: return "Rejected"
    if not item_id or not item.get("name") or not item.get("category"): return "Missing required data"
    if normalized(str(item["name"])) in governed_terms: return "Probable duplicate"
    caps = set(item.get("capabilities", []))
    if not caps or not caps <= CAPABILITIES: return "Invalid capability combination"
    if item.get("taxonomySupported") is False: return "Unsupported taxonomy"
    if not item.get("safety"): return "Requires safety review"
    if not item.get("evidenceReviewed"): return "Requires scientific/editorial review"
    return "Ready to normalize"


@dataclass(frozen=True)
class StoredDocument:
    fields: dict[str, Any]
    update_time: str | None = None


class CatalogueStore(Protocol):
    def get(self, path: str) -> StoredDocument | None: ...
    def list(self, path: str) -> list[StoredDocument]: ...
    def create_many(self, documents: list[tuple[str, dict[str, Any]]]) -> None: ...
    def replace(self, path: str, fields: dict[str, Any], update_time: str | None = None) -> None: ...


def _wire(value: Any) -> dict[str, Any]:
    if value is None: return {"nullValue": None}
    if isinstance(value, bool): return {"booleanValue": value}
    if isinstance(value, int): return {"integerValue": str(value)}
    if isinstance(value, float): return {"doubleValue": value}
    if isinstance(value, str): return {"stringValue": value}
    if isinstance(value, list): return {"arrayValue": {"values": [_wire(x) for x in value]}}
    if isinstance(value, dict): return {"mapValue": {"fields": {k: _wire(v) for k, v in value.items()}}}
    raise TypeError(type(value).__name__)


def _plain(value: dict[str, Any]) -> Any:
    for key, converter in (("nullValue", lambda _: None), ("booleanValue", bool), ("integerValue", int),
                           ("doubleValue", float), ("stringValue", str), ("timestampValue", str)):
        if key in value: return converter(value[key])
    if "arrayValue" in value: return [_plain(x) for x in value["arrayValue"].get("values", [])]
    if "mapValue" in value: return {k: _plain(v) for k, v in value["mapValue"].get("fields", {}).items()}
    raise ValueError("unsupported Firestore value")


class FirestoreRestStore:
    def __init__(self, project: str, emulator_host: str | None, token: str | None):
        self.root = f"projects/{project}/databases/(default)/documents"
        self.base = f"http://{emulator_host}/v1" if emulator_host else "https://firestore.googleapis.com/v1"
        self.headers = {"Authorization": "Bearer owner" if emulator_host else f"Bearer {token}"}

    def _request(self, method: str, suffix: str, payload: Any = None, missing_ok: bool = False) -> Any:
        request = urllib.request.Request(f"{self.base}/{suffix}", data=canonical_json(payload).encode() if payload is not None else None,
            method=method, headers={"Content-Type": "application/json", **self.headers})
        try: raw = urllib.request.urlopen(request, timeout=30).read()
        except urllib.error.HTTPError as error:
            if missing_ok and error.code == 404: return None
            raise RuntimeError(f"Firestore request failed ({error.code}): {error.read().decode(errors='replace')[:500]}") from error
        return json.loads(raw) if raw else {}

    @staticmethod
    def _decode(doc: dict[str, Any]) -> StoredDocument:
        return StoredDocument({k: _plain(v) for k, v in doc.get("fields", {}).items()}, doc.get("updateTime"))

    def get(self, path: str) -> StoredDocument | None:
        result = self._request("GET", f"{self.root}/{path}", missing_ok=True)
        return None if result is None else self._decode(result)

    def list(self, path: str) -> list[StoredDocument]:
        result, documents, token = None, [], None
        while result is None or token:
            query = "?pageSize=1000" + ("&pageToken=" + urllib.parse.quote(token) if token else "")
            result = self._request("GET", f"{self.root}/{path}{query}")
            documents += [self._decode(x) for x in result.get("documents", [])]
            token = result.get("nextPageToken")
        return documents

    def _write(self, path: str, fields: dict[str, Any], condition: dict[str, Any] | None = None) -> dict[str, Any]:
        write = {"update": {"name": f"{self.root}/{path}", "fields": {k: _wire(v) for k, v in fields.items()}}}
        if condition: write["currentDocument"] = condition
        return write

    def _commit(self, writes: list[dict[str, Any]]) -> None:
        result = self._request("POST", f"{self.root}:commit", {"writes": writes})
        if len(result.get("writeResults", [])) != len(writes): raise RuntimeError("Firestore did not confirm every write")

    def create_many(self, documents: list[tuple[str, dict[str, Any]]]) -> None:
        if len(documents) > MAX_COMMIT_WRITES: raise ValueError("commit exceeds guarded write limit")
        self._commit([self._write(path, fields, {"exists": False}) for path, fields in documents])

    def replace(self, path: str, fields: dict[str, Any], update_time: str | None = None) -> None:
        self._commit([self._write(path, fields, {"updateTime": update_time} if update_time else None)])


def verify_release(store: CatalogueStore, release_id: str, version: str, count: int, checksum: str) -> dict[str, Any]:
    stored = store.get(f"exercise_catalogue_releases/{release_id}")
    if stored is None: raise ValueError("immutable release is missing")
    required = {"releaseId": release_id, "catalogueVersion": version, "exerciseCount": count,
        "contentSha256": checksum, "schemaVersion": 1, "status": "published", "channel": "production", "validationStatus": "validated"}
    wrong = [k for k, v in required.items() if stored.fields.get(k) != v]
    if wrong: raise ValueError("release metadata mismatch: " + ", ".join(wrong))
    documents = [x.fields for x in store.list(f"exercise_catalogue_releases/{release_id}/exercises")]
    errors = validate_exercises(documents)
    if errors: raise ValueError("invalid release documents: " + "; ".join(errors))
    ids = [x["exerciseId"] for x in documents]
    if len(documents) != count or len(ids) != len(set(ids)): raise ValueError("release count or identity mismatch")
    actual = sha256(sorted(documents, key=lambda x: x["exerciseId"]))
    if actual != checksum: raise ValueError("release payload checksum mismatch")
    return {"verified": True, "releaseId": release_id, "catalogueVersion": version, "exerciseCount": count,
        "contentSha256": actual, "status": "published"}


def publish_release(store: CatalogueStore, release: dict[str, Any], exercises: list[dict[str, Any]]) -> dict[str, Any]:
    release_id, metadata_path = release["releaseId"], f"exercise_catalogue_releases/{release['releaseId']}"
    existing = store.get(metadata_path)
    if existing:
        if existing.fields.get("status") != "published": raise ValueError("partial immutable release exists; refusing to continue")
        receipt = verify_release(store, release_id, release["catalogueVersion"], len(exercises), release["contentSha256"])
        if existing.fields != release: raise ValueError("existing release metadata differs")
        return {**receipt, "published": False, "idempotent": True, "activated": False}
    incomplete = {**release, "status": "publishing", "validationStatus": "incomplete"}
    store.create_many([(metadata_path, incomplete)])
    for offset in range(0, len(exercises), MAX_COMMIT_WRITES):
        store.create_many([(f"{metadata_path}/exercises/{x['exerciseId']}", x) for x in exercises[offset:offset + MAX_COMMIT_WRITES]])
    actual = [x.fields for x in store.list(f"{metadata_path}/exercises")]
    if len(actual) != len(exercises) or sha256(sorted(actual, key=lambda x: x["exerciseId"])) != release["contentSha256"]:
        raise ValueError("publication verification failed; release remains incomplete")
    current = store.get(metadata_path)
    if current is None or current.fields != incomplete: raise ValueError("incomplete release metadata changed unexpectedly")
    store.replace(metadata_path, release, current.update_time)
    return {**verify_release(store, release_id, release["catalogueVersion"], len(exercises), release["contentSha256"]),
        "published": True, "idempotent": False, "activated": False}


def activate_release(store: CatalogueStore, release_id: str, version: str, count: int, checksum: str, activated_at: str) -> dict[str, Any]:
    verified = verify_release(store, release_id, version, count, checksum)
    previous = store.get("exercise_catalogue/current")
    old = previous.fields if previous else {}
    if old.get("releaseId") == release_id and old.get("contentSha256") == checksum:
        return {"activated": False, "idempotent": True, "previousReleaseId": release_id, "previousChecksum": checksum,
            "newReleaseId": release_id, "newChecksum": checksum}
    pointer = {"releaseId": release_id, "schemaVersion": 1, "catalogueVersion": version, "exerciseCount": count,
        "contentSha256": checksum, "status": "published", "channel": "production", "activatedAt": activated_at}
    if previous:
        store.replace("exercise_catalogue/current", pointer, previous.update_time)
    else:
        store.create_many([("exercise_catalogue/current", pointer)])
    return {"activated": True, "idempotent": False, "previousReleaseId": old.get("releaseId"),
        "previousChecksum": old.get("contentSha256"), "newReleaseId": verified["releaseId"], "newChecksum": verified["contentSha256"]}


def _store(args: argparse.Namespace) -> FirestoreRestStore:
    if args.project.startswith("demo-"):
        host = os.environ.get("FIRESTORE_EMULATOR_HOST")
        if host not in {"127.0.0.1:8080", "localhost:8080"}: raise ValueError("explicit local Firestore emulator required")
        return FirestoreRestStore(args.project, host, None)
    if args.project != PRODUCTION_PROJECT: raise ValueError("non-demo project must be exactly hv1-platform")
    if not args.confirm_production: raise ValueError("production confirmation is required")
    token = os.environ.get("FIREBASE_ADMIN_ACCESS_TOKEN")
    if not token: raise ValueError("authorized Firebase Admin access token is unavailable")
    return FirestoreRestStore(args.project, None, token)


def _network(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--project", required=True); parser.add_argument("--release-id", required=True)
    parser.add_argument("--expected-version", required=True); parser.add_argument("--expected-count", required=True, type=int)
    parser.add_argument("--expected-checksum", required=True); parser.add_argument("--confirm-production", action="store_true")


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(); subs = parser.add_subparsers(dest="operation", required=True)
    generate = subs.add_parser("generate"); generate.add_argument("--source", type=Path, default=Path("app/src/main/assets/strength-exercise-catalogue.json"))
    generate.add_argument("--release-id", required=True); generate.add_argument("--published-at", required=True); generate.add_argument("--output", type=Path)
    publish = subs.add_parser("publish"); _network(publish); publish.add_argument("--source", type=Path, default=Path("app/src/main/assets/strength-exercise-catalogue.json")); publish.add_argument("--published-at", required=True)
    verify = subs.add_parser("verify"); _network(verify)
    for name in ("activate", "rollback"):
        command = subs.add_parser(name); _network(command); command.add_argument("--activated-at", required=True)
    args = parser.parse_args(list(argv) if argv is not None else None)
    if args.operation == "generate":
        source = json.loads(args.source.read_text(encoding="utf-8")); release, exercises = build_release(source, args.release_id, args.published_at)
        if args.output:
            args.output.mkdir(parents=True, exist_ok=True)
            (args.output / "manifest.json").write_text(canonical_json(release) + "\n", encoding="utf-8")
            (args.output / "exercises.json").write_text(canonical_json(exercises) + "\n", encoding="utf-8")
        result = {"valid": True, "mode": "dry-run", "operation": "generate", "releaseId": release["releaseId"],
            "catalogueVersion": release["catalogueVersion"], "exerciseCount": len(exercises), "contentSha256": release["contentSha256"],
            "manifestSha256": sha256(release), "publicationPayloadSha256": sha256({"manifest": release, "exercises": exercises})}
    elif args.operation == "publish":
        source = json.loads(args.source.read_text(encoding="utf-8")); release, exercises = build_release(source, args.release_id, args.published_at)
        if (release["catalogueVersion"], release["exerciseCount"], release["contentSha256"]) != (args.expected_version, args.expected_count, args.expected_checksum):
            raise ValueError("candidate does not match explicit expectations")
        result = publish_release(_store(args), release, exercises)
    elif args.operation == "verify": result = verify_release(_store(args), args.release_id, args.expected_version, args.expected_count, args.expected_checksum)
    else:
        result = activate_release(_store(args), args.release_id, args.expected_version, args.expected_count, args.expected_checksum, args.activated_at)
        result["operation"] = args.operation
    print(canonical_json(result)); return 0


if __name__ == "__main__": raise SystemExit(main())
