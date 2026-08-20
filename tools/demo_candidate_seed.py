#!/usr/bin/env python3
"""Seed and activate an approved candidate only in an explicit loopback demo Firestore."""
import argparse, json, os
from pathlib import Path
from governed_catalogue import FirestoreRestStore, activate_release, publish_release, verify_release
from ontology_editorial import canonical_json

def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument("--package",type=Path,required=True); parser.add_argument("--project",required=True); args=parser.parse_args()
    host=os.environ.get("FIRESTORE_EMULATOR_HOST","")
    if not args.project.startswith("demo-") or host not in {"127.0.0.1:8080","localhost:8080"}: raise ValueError("loopback demo Firestore required")
    release=json.loads((args.package/"release-metadata.json").read_text(encoding="utf-8")); exercises=json.loads((args.package/"release-exercises.json").read_text(encoding="utf-8"))
    store=FirestoreRestStore(args.project,host,None); published=publish_release(store,release,exercises)
    verified=verify_release(store,release["releaseId"],release["catalogueVersion"],len(exercises),release["contentSha256"])
    activated=activate_release(store,release["releaseId"],release["catalogueVersion"],len(exercises),release["contentSha256"],"2026-08-20T00:10:00Z")
    result={"published":published.get("published") or published.get("idempotent"),"verified":verified["verified"],"activated":activated.get("activated") or activated.get("idempotent"),"exerciseCount":len(exercises)}
    print(canonical_json(result)); return 0
if __name__=="__main__": raise SystemExit(main())
