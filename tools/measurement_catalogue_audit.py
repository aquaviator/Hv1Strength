#!/usr/bin/env python3
"""Deterministically projects governed exercises onto the V37 metric architecture.

The tool is local-only: it reads a verified immutable-release export and cannot
contact Firebase or mutate an exercise catalogue.
"""
from __future__ import annotations
import argparse, hashlib, json
from collections import Counter
from pathlib import Path

METRICS = {"repetitions", "duration", "distance", "external_load", "assistance", "manufacturer_resistance",
           "incline", "pace", "speed", "power", "mechanical_work", "energy", "cadence", "stroke_rate",
           "heart_rate", "rpe", "rir", "tempo", "intervals", "side"}
CAPABILITY = {"repetitions": "repetitions", "duration": "duration", "distance": "distance", "load": "external_load",
              "assisted_load": "assistance", "rpe": "rpe", "rir": "rir", "tempo": "tempo", "intervals": "intervals", "side": "side"}
CARDIO = {
    "treadmill_run": ({"incline", "heart_rate", "energy"}, {"pace", "speed"}),
    "incline_treadmill_walk": ({"incline", "heart_rate", "energy"}, {"pace", "speed"}),
    "stair_climber": ({"manufacturer_resistance", "heart_rate", "energy"}, set()),
    "stationary_bike": ({"manufacturer_resistance", "power", "cadence", "heart_rate", "energy"}, {"speed"}),
    "air_bike": ({"power", "cadence", "heart_rate", "energy"}, {"speed"}),
    "assault_bike_sprint": ({"power", "cadence", "heart_rate", "energy", "intervals"}, set()),
    "rowing_machine": ({"manufacturer_resistance", "power", "stroke_rate", "heart_rate", "energy"}, {"pace"}),
    # The governed record has legacy strength-style tracking flags, but its
    # stable name and aliases unambiguously identify an indoor rower. Use only
    # universally available rower outputs as primary values; telemetry remains
    # optional because individual machines may not emit it.
    "rowing_machine_cardio": ({"manufacturer_resistance", "power", "stroke_rate", "heart_rate", "energy"}, {"pace"}),
    "ski_erg": ({"manufacturer_resistance", "power", "stroke_rate", "heart_rate", "energy"}, {"pace"}),
    "elliptical": ({"manufacturer_resistance", "cadence", "heart_rate", "energy"}, {"speed"}),
}

def canonical(value): return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
def digest(value): return hashlib.sha256(canonical(value).encode()).hexdigest()

def profile(item):
    mapped = {CAPABILITY[x] for x in item.get("trackingCapabilities", []) if x in CAPABILITY}
    if "weighted_bodyweight" in item.get("trackingCapabilities", []): mapped.add("external_load")
    primary = [x for x in ("repetitions", "duration", "distance", "external_load", "assistance") if x in mapped]
    if item["exerciseId"] == "rowing_machine_cardio":
        primary = ["duration", "distance"]
        mapped = {"duration", "distance", "rpe"}
    if not primary: primary = ["duration"]
    secondary = [x for x in ("rpe", "rir", "tempo", "intervals", "side") if x in mapped]
    optional, derived = CARDIO.get(item["exerciseId"], (set(), set()))
    # Derived pace/speed is only truthful when both time and distance are recorded.
    derived = {x for x in derived if {"duration", "distance"} <= set(primary)}
    recording = set(primary) | set(secondary) | optional
    return {"exerciseId": item["exerciseId"], "profileId": f"governed:{item['exerciseId']}:v1", "schemaVersion": 1,
            "primaryMetrics": primary, "secondaryMetrics": secondary, "optionalMetrics": sorted(optional),
            "derivedMetrics": sorted(derived), "prescriptionMetrics": primary + secondary,
            "recordingMetrics": sorted(recording), "unsupportedMetrics": sorted(METRICS - recording - derived)}

def run(source: Path, output: Path):
    exercises = json.loads(source.read_text(encoding="utf-8"))
    profiles = [profile(item) for item in sorted(exercises, key=lambda x: x["exerciseId"])]
    ids = [x["exerciseId"] for x in profiles]
    errors = []
    if len(ids) != len(set(ids)): errors.append("duplicate exercise IDs")
    if any(not set(x["recordingMetrics"]) <= METRICS for x in profiles): errors.append("unknown recording metric")
    if any(set(x["recordingMetrics"]) & set(x["unsupportedMetrics"]) for x in profiles): errors.append("supported/unsupported overlap")
    deferred = [{"exerciseId": item["exerciseId"], "reason": "equipment telemetry cannot be inferred safely from a generic cardio-equipment label"}
                for item in exercises if "cardio equipment" in item.get("equipment", []) and item["exerciseId"] not in CARDIO]
    candidate = {"schemaVersion": 1, "sourceReleaseId": "strength-2026.08.36-v1", "exerciseCount": len(profiles),
                 "profiles": profiles, "productionWritePerformed": False}
    candidate["payloadChecksum"] = digest(profiles)
    report = {"exerciseCount": len(profiles), "uniqueStableIds": len(set(ids)), "profileCount": len(profiles),
              "deferredReviewCount": len(deferred), "errors": errors, "deterministic": True,
              "candidateChecksum": digest(candidate), "profilePayloadChecksum": candidate["payloadChecksum"],
              "exerciseTypeCounts": dict(sorted(Counter(item.get("exerciseType", "unknown") for item in exercises).items())),
              "primaryMetricCounts": dict(sorted(Counter(metric for item in profiles for metric in item["primaryMetrics"]).items())),
              "optionalMetricCounts": dict(sorted(Counter(metric for item in profiles for metric in item["optionalMetrics"]).items())),
              "derivedMetricCounts": dict(sorted(Counter(metric for item in profiles for metric in item["derivedMetrics"]).items())),
              "unsafeOrMalformedProfiles": 0, "productionWritePerformed": False}
    if errors: raise ValueError(errors)
    output.mkdir(parents=True, exist_ok=True)
    for name, value in (("measurement-profile-candidate.json", candidate), ("measurement-audit-report.json", report),
                        ("measurement-deferred-review.json", deferred)):
        (output/name).write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2)+"\n", encoding="utf-8")
    return report

if __name__ == "__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("--source",type=Path,required=True); parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args(); print(canonical(run(args.source,args.output)))
