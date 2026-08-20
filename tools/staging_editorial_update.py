#!/usr/bin/env python3
"""Guarded metadata-only updater for a generated V36 editorial approval package."""
import argparse, json, os, sys, urllib.error, urllib.request
from pathlib import Path
from typing import Any

from ontology_editorial import canonical_json, digest, list_documents

PROJECT, DATABASE, COLLECTION, COUNT, BATCH = "hv1-platform", "(default)", "staging_exercises", 870, 100
ALLOWED = {"editorialState", "reviewDecision", "reviewReasons", "reviewerType", "reviewedSourceChecksum",
           "approvedPublishableProjection", "approvedProjectionChecksum", "excludedFieldPaths", "duplicateTarget",
           "outstandingSpecialistRequirements", "reviewSchemaVersion"}


def wire(value: Any) -> dict[str, Any]:
    if value is None: return {"nullValue": None}
    if isinstance(value, bool): return {"booleanValue": value}
    if isinstance(value, int): return {"integerValue": str(value)}
    if isinstance(value, float): return {"doubleValue": value}
    if isinstance(value, str): return {"stringValue": value}
    if isinstance(value, list): return {"arrayValue": {"values": [wire(item) for item in value]}}
    if isinstance(value, dict): return {"mapValue": {"fields": {key: wire(item) for key, item in value.items()}}}
    raise TypeError(type(value).__name__)


def commit(token: str, batch: list[tuple[dict[str, Any], str]]) -> None:
    root = f"projects/{PROJECT}/databases/{DATABASE}/documents"
    writes = []
    for update, update_time in batch:
        fields = update["fields"]
        writes.append({"update": {"name": f"{root}/{COLLECTION}/{update['documentId']}",
                                   "fields": {key: wire(value) for key, value in fields.items()}},
                       "updateMask": {"fieldPaths": sorted(fields)}, "currentDocument": {"updateTime": update_time}})
    url = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/{DATABASE}/documents:commit"
    request = urllib.request.Request(url, data=canonical_json({"writes": writes}).encode(), method="POST",
                                     headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=120) as response: result = json.loads(response.read())
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"editorial metadata commit failed ({error.code}): {error.read().decode(errors='replace')[:500]}") from error
    if len(result.get("writeResults", [])) != len(writes): raise RuntimeError("not every update was acknowledged")


def main() -> int:
    parser = argparse.ArgumentParser(); parser.add_argument("--package", type=Path, required=True); args = parser.parse_args()
    token = os.environ.get("FIREBASE_ADMIN_ACCESS_TOKEN", "")
    if not token: raise ValueError("short-lived write credential unavailable")
    folder = args.package
    snapshot = json.loads((folder / "review-production-snapshot.json").read_text(encoding="utf-8"))
    updates = json.loads((folder / "staging-editorial-update.json").read_text(encoding="utf-8"))
    manifest = json.loads((folder / "staging-editorial-update-manifest.json").read_text(encoding="utf-8"))
    if (manifest.get("project"), manifest.get("database"), manifest.get("collection"), manifest.get("recordCount")) != (PROJECT, DATABASE, COLLECTION, COUNT): raise ValueError("target/count mismatch")
    if digest(updates) != manifest.get("updatePayloadChecksum"): raise ValueError("update payload checksum mismatch")
    if manifest.get("publishedStatePresent") or manifest.get("releaseOrPointerPathsPresent"): raise ValueError("forbidden publication/path")
    if len(updates) != COUNT or len({item["documentId"] for item in updates}) != COUNT: raise ValueError("update IDs incomplete")
    for item in updates:
        if set(item["fields"]) != ALLOWED or item["fields"].get("editorialState") == "PUBLISHED": raise ValueError("unauthorized field/state")
        if digest(item["fields"]) != item["updateChecksum"]: raise ValueError("record update checksum mismatch")
    expected = {item["documentId"]: item["fields"] for item in snapshot["stagingDocuments"]}
    actual, _ = list_documents(token, PROJECT, COLLECTION); actual_by_id = {item["documentId"]: item for item in actual}
    if set(actual_by_id) != set(expected): raise ValueError("staging set changed since review")
    update_by_id = {item["documentId"]: item for item in updates}; pending, identical = [], 0
    for document_id in sorted(expected):
        current, base, desired = actual_by_id[document_id], expected[document_id], update_by_id[document_id]["fields"]
        if any(current["fields"].get(key) != value for key, value in base.items()): raise ValueError(f"source changed: {document_id}")
        extra = set(current["fields"]) - set(base)
        if extra:
            if extra != ALLOWED or any(current["fields"].get(key) != value for key, value in desired.items()): raise ValueError(f"review conflict: {document_id}")
            identical += 1
        else: pending.append((update_by_id[document_id], current["updateTime"]))
    updated = 0
    try:
        for offset in range(0, len(pending), BATCH): commit(token, pending[offset:offset+BATCH]); updated += len(pending[offset:offset+BATCH])
    except Exception:
        print(canonical_json({"complete": False, "updatedBeforeFailure": updated, "identical": identical})); raise
    result = {"complete": True, "authorized": COUNT, "updated": updated, "identical": identical, "conflicting": 0,
              "failed": 0, "target": f"{PROJECT}/{DATABASE}/{COLLECTION}", "updatePayloadChecksum": manifest["updatePayloadChecksum"]}
    (folder / "staging-editorial-update-receipt.json").write_text(json.dumps(result, sort_keys=True, indent=2)+"\n", encoding="utf-8")
    print(canonical_json(result)); return 0


if __name__ == "__main__": raise SystemExit(main())
