#!/usr/bin/env python3
"""Guarded demo-only lifecycle acceptance for an approved catalogue package."""
from __future__ import annotations
import argparse, json, os
from pathlib import Path
from typing import Any

from governed_catalogue import FirestoreRestStore, StoredDocument, activate_release, publish_release, sha256, verify_release
from ontology_editorial import canonical_json


class GuardedStore:
    def __init__(self, inner: FirestoreRestStore): self.inner, self.paths = inner, []
    def guard(self, path: str) -> None:
        self.paths.append(path)
        if path.startswith(("users/", "staging_exercises/", "accounts/", "memberships/", "workouts/", "planner/", "commands/")):
            raise ValueError("lifecycle harness attempted forbidden collection")
    def get(self, path: str) -> StoredDocument | None: self.guard(path); return self.inner.get(path)
    def list(self, path: str) -> list[StoredDocument]: self.guard(path); return self.inner.list(path)
    def create_many(self, documents: list[tuple[str, dict[str, Any]]]) -> None:
        for path, _ in documents: self.guard(path)
        self.inner.create_many(documents)
    def replace(self, path: str, fields: dict[str, Any], update_time: str | None = None) -> None:
        self.guard(path); self.inner.replace(path, fields, update_time)


def metadata(release_id: str, version: str, exercises: list[dict[str, Any]], previous: str | None) -> dict[str, Any]:
    return {"releaseId": release_id, "schemaVersion": 1, "catalogueVersion": version, "exerciseCount": len(exercises),
            "contentSha256": sha256(exercises), "publishedAt": "2026-08-20T00:00:00Z", "minimumStrengthVersionCode": 36,
            "minimumHiitVersionCode": 1, "status": "published", "channel": "production", "previousReleaseId": previous,
            "publisherToolVersion": "human-v1-governed-publisher/2.0", "validationStatus": "validated",
            "editorialNotes": "Local demo-only lifecycle baseline."}


def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument("--package",type=Path,required=True); parser.add_argument("--snapshot",type=Path,required=True); parser.add_argument("--project",required=True); args=parser.parse_args()
    host=os.environ.get("FIRESTORE_EMULATOR_HOST","")
    if not args.project.startswith("demo-") or host not in {"127.0.0.1:8080","localhost:8080"}: raise ValueError("explicit loopback demo Firestore required")
    package=json.loads((args.package/"release-metadata.json").read_text(encoding="utf-8")); exercises=json.loads((args.package/"release-exercises.json").read_text(encoding="utf-8"))
    snapshot=json.loads(args.snapshot.read_text(encoding="utf-8")); baseline_exercises=sorted((item["fields"] for item in snapshot["governedExercises"]),key=lambda item:item["exerciseId"])
    baseline=metadata("local-strength-baseline-v1","2026.08.32-local",baseline_exercises,None)
    store=GuardedStore(FirestoreRestStore(args.project,host,None))
    publish_release(store,baseline,baseline_exercises)
    activate_release(store,baseline["releaseId"],baseline["catalogueVersion"],baseline["exerciseCount"],baseline["contentSha256"],"2026-08-20T00:00:00Z")
    pointer_before=store.get("exercise_catalogue/current").fields
    published=publish_release(store,package,exercises)
    pointer_after_publish=store.get("exercise_catalogue/current").fields
    if pointer_after_publish!=pointer_before: raise ValueError("publication changed current pointer")
    verified=verify_release(store,package["releaseId"],package["catalogueVersion"],len(exercises),package["contentSha256"])
    activated=activate_release(store,package["releaseId"],package["catalogueVersion"],len(exercises),package["contentSha256"],"2026-08-20T00:01:00Z")
    idempotent_read=verify_release(store,package["releaseId"],package["catalogueVersion"],len(exercises),package["contentSha256"])
    identical=publish_release(store,package,exercises)
    conflict_refused=incomplete_refused=staging_activation_refused=False
    try: publish_release(store,{**package,"editorialNotes":"conflicting duplicate"},exercises)
    except ValueError: conflict_refused=True
    incomplete={**package,"releaseId":"local-incomplete-v1","status":"publishing","validationStatus":"incomplete"}
    store.create_many([("exercise_catalogue_releases/local-incomplete-v1",incomplete)])
    try: activate_release(store,"local-incomplete-v1",package["catalogueVersion"],len(exercises),package["contentSha256"],"2026-08-20T00:02:00Z")
    except ValueError: incomplete_refused=True
    try: activate_release(store,"staging_exercises",package["catalogueVersion"],len(exercises),package["contentSha256"],"2026-08-20T00:02:00Z")
    except ValueError: staging_activation_refused=True
    rollback=activate_release(store,baseline["releaseId"],baseline["catalogueVersion"],baseline["exerciseCount"],baseline["contentSha256"],"2026-08-20T00:03:00Z")
    immutable=verify_release(store,package["releaseId"],package["catalogueVersion"],len(exercises),package["contentSha256"])
    result={"published":published["published"],"independentlyVerified":verified["verified"],"pointerUnchangedAfterPublication":pointer_after_publish==pointer_before,
            "activated":activated["activated"],"idempotentRead":idempotent_read["verified"],"identicalPublicationIdempotent":identical["idempotent"],
            "conflictingDuplicateRefused":conflict_refused,"incompleteActivationRefused":incomplete_refused,"stagingActivationRefused":staging_activation_refused,
            "rolledBack":rollback["activated"],"immutableUnchanged":immutable["contentSha256"]==package["contentSha256"],
            "userCollectionsTouched":any(path.startswith(("users/","accounts/")) for path in store.paths),"productionReachable":False,"exerciseCount":len(exercises)}
    if not all(value for key,value in result.items() if key not in {"userCollectionsTouched","productionReachable","exerciseCount"}) or result["userCollectionsTouched"]: raise ValueError("lifecycle acceptance failed")
    (args.package/"local-firestore-lifecycle-receipt.json").write_text(json.dumps(result,sort_keys=True,indent=2)+"\n",encoding="utf-8")
    print(canonical_json(result)); return 0


if __name__=="__main__": raise SystemExit(main())
