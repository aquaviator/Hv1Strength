"""Demo-only V35 governed-catalogue acceptance release generator.

This harness composes immutable variants through the real governed publisher.
It cannot target production or run without an explicit local emulator endpoint.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

import governed_catalogue as governed

DEMO_EXERCISE_ID = "v35_demo_sled_march"


def variant_release(source: dict, variant: str, release_id: str, published_at: str, previous: str | None):
    release, exercises = governed.build_release(source, release_id, published_at)
    if variant in {"added", "editorial", "deprecated"}:
        demo = copy.deepcopy(next(item for item in exercises if item["exerciseId"] == "incline_treadmill_walk"))
        demo.update({
            "exerciseId": DEMO_EXERCISE_ID,
            "canonicalName": "Demo Sled March",
            "displayName": "Demo Sled March",
            "aliases": ["Acceptance Sled March"],
            "normalizedNames": ["acceptance sled march", "demo sled march"],
            "summary": "A controlled demo conditioning exercise for the local V35 acceptance environment.",
            "category": "Cardio",
            "exerciseType": "conditioning",
            "movementPatterns": ["loaded carry"],
            "primaryMuscles": ["quadriceps", "glutes"],
            "secondaryMuscles": ["calves", "core"],
            "equipment": ["sled"],
            "trackingCapabilities": ["distance", "duration"],
            "timedCompatible": True,
            "recommendedForStrength": True,
            "recommendedForHiit": True,
            "cardioSuitable": True,
            "circuitSuitable": True,
            "timedIntervalSuitable": True,
            "progressionIds": [], "regressionIds": [], "relatedExerciseIds": [],
            "replacementExerciseId": None, "suppressedAliases": [],
            "contentRevision": 1, "deprecated": False, "editorialStatus": "approved",
            "createdAt": published_at, "updatedAt": published_at,
        })
        exercises.append(demo)
    if variant in {"editorial", "deprecated"}:
        bench = next(item for item in exercises if item["exerciseId"] == "bench_press")
        bench["displayName"] = "Bench Press — Governed"
        bench["summary"] = "A governed horizontal press exercise using a barbell."
        bench["contentRevision"] = 2
        bench["updatedAt"] = published_at
    if variant == "deprecated":
        squat = next(item for item in exercises if item["exerciseId"] == "squat")
        squat["deprecated"] = True
        squat["editorialStatus"] = "deprecated"
        squat["replacementExerciseId"] = "goblet_squat"
        squat["contentRevision"] = 2
        squat["updatedAt"] = published_at
    exercises.sort(key=lambda item: item["exerciseId"])
    errors = governed.validate_exercises(exercises, {item["id"] for item in source["exercises"]})
    if errors:
        raise ValueError("; ".join(errors))
    release.update({
        "catalogueVersion": f"2026.08.35-demo-{variant}",
        "exerciseCount": len(exercises),
        "contentSha256": governed.sha256(exercises),
        "minimumStrengthVersionCode": 35,
        "previousReleaseId": previous,
        "editorialNotes": f"Local demo-only V35 {variant} acceptance release.",
    })
    return release, exercises


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--variant", choices=("added", "editorial", "deprecated"), required=True)
    parser.add_argument("--release-id", required=True)
    parser.add_argument("--previous-release-id")
    parser.add_argument("--published-at", required=True)
    parser.add_argument("--project", default="demo-hv1-strength-local")
    parser.add_argument("--source", type=Path, default=Path("app/src/main/assets/strength-exercise-catalogue.json"))
    parser.add_argument("--output", type=Path)
    operation = parser.add_mutually_exclusive_group()
    operation.add_argument("--publish-emulator", action="store_true")
    operation.add_argument("--activate-emulator", action="store_true")
    args = parser.parse_args()
    if not args.project.startswith("demo-"):
        raise ValueError("V35 acceptance variants require a demo- project")
    source = json.loads(args.source.read_text(encoding="utf-8"))
    release, exercises = variant_release(source, args.variant, args.release_id, args.published_at, args.previous_release_id)
    if args.output:
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "manifest.json").write_text(governed.canonical_json(release) + "\n", encoding="utf-8")
        (args.output / "exercises.json").write_text(governed.canonical_json(exercises) + "\n", encoding="utf-8")
    if args.publish_emulator or args.activate_emulator:
        host = governed.os.environ.get("FIRESTORE_EMULATOR_HOST")
        if host not in {"127.0.0.1:8080", "localhost:8080"}:
            raise ValueError("explicit local Firestore emulator required")
        store = governed.FirestoreRestStore(args.project, host, None)
    if args.publish_emulator:
        governed.publish_release(store, release, exercises)
    if args.activate_emulator:
        governed.activate_release(
            store, release["releaseId"], release["catalogueVersion"],
            release["exerciseCount"], release["contentSha256"], args.published_at
        )
    print(governed.canonical_json({
        "valid": True, "mode": "emulator-publish" if args.publish_emulator else "emulator-activate" if args.activate_emulator else "dry-run",
        "variant": args.variant, "releaseId": args.release_id,
        "exerciseCount": len(exercises), "contentSha256": release["contentSha256"],
    }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
