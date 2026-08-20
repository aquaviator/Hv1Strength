#!/usr/bin/env python3
"""Independent read-only verification of V36 staging editorial decisions."""
import argparse, json, os
from pathlib import Path

from ontology_editorial import EXPECTED_CHECKSUM, RELEASE_ID, canonical_json, get_document, list_documents

EDITORIAL_FIELDS = {"editorialState", "reviewDecision", "reviewReasons", "reviewerType", "reviewedSourceChecksum",
                    "approvedPublishableProjection", "approvedProjectionChecksum", "excludedFieldPaths", "duplicateTarget",
                    "outstandingSpecialistRequirements", "reviewSchemaVersion"}


def main() -> int:
    parser = argparse.ArgumentParser(); parser.add_argument("--package", type=Path, required=True); args = parser.parse_args()
    token = os.environ.get("FIREBASE_ADMIN_ACCESS_TOKEN", "")
    if not token: raise ValueError("short-lived read credential unavailable")
    folder = args.package
    snapshot = json.loads((folder / "review-production-snapshot.json").read_text(encoding="utf-8"))
    updates = json.loads((folder / "staging-editorial-update.json").read_text(encoding="utf-8"))
    expected_source = {item["documentId"]: item["fields"] for item in snapshot["stagingDocuments"]}
    expected_update = {item["documentId"]: item["fields"] for item in updates}
    actual, pages = list_documents(token, "hv1-platform", "staging_exercises")
    actual_by_id = {item["documentId"]: item["fields"] for item in actual}
    missing = sorted(set(expected_source) - set(actual_by_id)); unexpected = sorted(set(actual_by_id) - set(expected_source))
    source_changed, metadata_different, published, invalid = [], [], [], []
    for document_id in sorted(set(expected_source) & set(actual_by_id)):
        fields = actual_by_id[document_id]
        if any(fields.get(key) != value for key, value in expected_source[document_id].items() if key not in EDITORIAL_FIELDS): source_changed.append(document_id)
        if any(fields.get(key) != value for key, value in expected_update[document_id].items()): metadata_different.append(document_id)
        if fields.get("editorialState") == "PUBLISHED": published.append(document_id)
        decision, projection = fields.get("reviewDecision"), fields.get("approvedPublishableProjection")
        if not decision or (decision.startswith("APPROVE_") and not projection) or (not decision.startswith("APPROVE_") and projection): invalid.append(document_id)
        if not fields.get("originalOntologySourceId") or not ((fields.get("exercise") or {}).get("source")): invalid.append(document_id)
    pointer = get_document(token, "hv1-platform", "exercise_catalogue/current")["fields"]
    release = get_document(token, "hv1-platform", f"exercise_catalogue_releases/{RELEASE_ID}")["fields"]
    pointer_unchanged = pointer == snapshot["currentPointer"]
    release_unchanged = release == snapshot["releaseMetadata"] and release.get("contentSha256") == EXPECTED_CHECKSUM
    result = {"readOnly": True, "actual": len(actual), "verified": 870-len(set(missing+source_changed+metadata_different+invalid)),
              "missing": len(missing), "unexpected": len(unexpected), "sourceChanged": len(source_changed),
              "metadataDifferent": len(metadata_different), "invalidDecisionState": len(set(invalid)), "published": len(published),
              "pointerUnchanged": pointer_unchanged, "releaseUnchanged": release_unchanged, "pages": pages}
    if any((missing, unexpected, source_changed, metadata_different, published, invalid)) or not pointer_unchanged or not release_unchanged or len(actual) != 870:
        raise RuntimeError(canonical_json(result))
    (folder / "staging-editorial-verification-receipt.json").write_text(json.dumps(result, sort_keys=True, indent=2)+"\n", encoding="utf-8")
    print(canonical_json(result)); return 0


if __name__ == "__main__": raise SystemExit(main())
