#!/usr/bin/env python3
"""Local-only governed exercise editorial workflow.

The module deliberately has no Firebase dependency.  It turns untrusted staging
records into explainable recommendations; only an explicit APPROVED decision can
enter a deterministic release draft.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import unicodedata
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

LIFECYCLE = ("INGESTED", "CLASSIFIED", "REVIEWED", "APPROVED", "REJECTED", "PUBLISHED")
DECISIONS = ("NEW_EXERCISE", "ALIAS", "ENRICHMENT", "REJECT")
TRANSITIONS = {
    "INGESTED": {"CLASSIFIED"}, "CLASSIFIED": {"REVIEWED"},
    "REVIEWED": {"APPROVED", "REJECTED"}, "APPROVED": {"PUBLISHED"},
    "REJECTED": set(), "PUBLISHED": set(),
}
REQUIRED_EVIDENCE = {"claim", "citation", "sourceUrl", "reviewedAt", "reviewer"}
RICH_LIST_FIELDS = {
    "secondaryCategories", "modalities", "stabilizers", "jointActions", "optionalEquipment",
    "substitutableEquipment", "environmentSuitability", "typicalUseCases", "contraindications",
    "cautions", "stopConditions", "clinicalSupervision", "programmingGuidance",
}


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKD", value)
    value = "".join(c for c in value if not unicodedata.combining(c)).lower()
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", value)).strip()


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256(value: Any) -> str:
    return hashlib.sha256(canonical_json(value).encode()).hexdigest()


def transition(record: dict[str, Any], target: str) -> dict[str, Any]:
    current = record.get("lifecycle", "INGESTED")
    if target not in TRANSITIONS.get(current, set()):
        raise ValueError(f"Invalid editorial transition: {current} -> {target}")
    return {**record, "lifecycle": target}


def validate_evidence(evidence: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    for index, item in enumerate(evidence):
        missing = sorted(k for k in REQUIRED_EVIDENCE if not str(item.get(k, "")).strip())
        if missing:
            errors.append(f"evidence[{index}] missing: {', '.join(missing)}")
        url = str(item.get("sourceUrl", ""))
        if url and not url.startswith("https://"):
            errors.append(f"evidence[{index}] sourceUrl must use https")
    return errors


def validate_rich_exercise(item: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    for field in ("id", "name", "category"):
        if not str(item.get(field, "")).strip(): errors.append(f"missing {field}")
    if item.get("id") and not re.fullmatch(r"[a-z][a-z0-9_]{2,63}", str(item["id"])):
        errors.append("invalid canonical id")
    for field in RICH_LIST_FIELDS:
        if field in item and not isinstance(item[field], list): errors.append(f"{field} must be a list")
    if "evidence" in item:
        if not isinstance(item["evidence"], list): errors.append("evidence must be a list")
        else: errors.extend(validate_evidence(item["evidence"]))
    return errors


@dataclass(frozen=True)
class Match:
    exercise_id: str
    score: float
    reasons: tuple[str, ...]


def classify(candidate: dict[str, Any], catalogue: list[dict[str, Any]]) -> dict[str, Any]:
    name = normalize(str(candidate.get("name", "")))
    candidate_terms = set(name.split())
    ranked: list[Match] = []
    for item in catalogue:
        canonical = normalize(str(item.get("name", "")))
        aliases = {normalize(str(v)) for v in item.get("aliases", [])}
        reasons: list[str] = []
        if candidate.get("id") == item.get("id"): score = 1.0; reasons.append("canonical ID exact")
        elif name == canonical: score = .99; reasons.append("canonical name exact")
        elif name in aliases: score = .98; reasons.append("known alias exact")
        else:
            lexical = SequenceMatcher(None, name, canonical).ratio()
            terms = set(canonical.split())
            overlap = len(candidate_terms & terms) / max(1, len(candidate_terms | terms))
            context = 0.0
            if normalize(str(candidate.get("category", ""))) == normalize(str(item.get("category", ""))):
                context += .08; reasons.append("same category")
            if set(map(normalize, candidate.get("equipment", []))) & set(map(normalize, item.get("equipment", []))):
                context += .06; reasons.append("equipment overlap")
            score = min(.97, lexical * .68 + overlap * .18 + context)
            reasons.insert(0, f"lexical similarity {lexical:.3f}")
        ranked.append(Match(str(item["id"]), round(score, 4), tuple(reasons)))
    ranked.sort(key=lambda m: (-m.score, m.exercise_id))
    best = ranked[0] if ranked else Match("", 0, ("catalogue empty",))
    recommendation = "ALIAS" if best.score >= .9 else "ENRICHMENT" if best.score >= .72 else "NEW_EXERCISE"
    return {
        "candidateId": candidate.get("candidateId"), "lifecycle": "CLASSIFIED",
        "recommendation": recommendation, "canonicalExerciseId": best.exercise_id or None,
        "confidence": best.score, "reasons": list(best.reasons),
        "alternatives": [{"exerciseId": m.exercise_id, "score": m.score} for m in ranked[:3]],
        "classifierVersion": "v36.1",
    }


def build_release_draft(base: dict[str, Any], candidates: list[dict[str, Any]], decisions: list[dict[str, Any]]) -> dict[str, Any]:
    approved = {d["candidateId"]: d for d in decisions if d.get("lifecycle") == "APPROVED"}
    exercises = {e["id"]: dict(e) for e in base["exercises"]}
    for candidate in sorted(candidates, key=lambda c: str(c.get("candidateId"))):
        decision = approved.get(candidate.get("candidateId"))
        if not decision or decision.get("decision") == "REJECT": continue
        kind = decision.get("decision")
        target = decision.get("canonicalExerciseId")
        if kind == "NEW_EXERCISE":
            item = dict(candidate["exercise"]); errors = validate_rich_exercise(item)
            if errors: raise ValueError("; ".join(errors))
            if item["id"] in exercises: raise ValueError(f"canonical ID already exists: {item['id']}")
            exercises[item["id"]] = item
        elif kind == "ALIAS":
            if target not in exercises: raise ValueError(f"unknown target: {target}")
            alias = str(candidate["exercise"]["name"])
            exercises[target]["aliases"] = sorted(set(exercises[target].get("aliases", []) + [alias]), key=normalize)
        elif kind == "ENRICHMENT":
            if target not in exercises: raise ValueError(f"unknown target: {target}")
            enrichment = candidate.get("enrichment", {})
            errors = validate_evidence(enrichment.get("evidence", []))
            if errors: raise ValueError("; ".join(errors))
            exercises[target].update(enrichment)
        else: raise ValueError(f"unsupported decision: {kind}")
    output = {**base, "exercises": [exercises[k] for k in sorted(exercises)]}
    output["exerciseCount"] = len(output["exercises"])
    output["payloadChecksum"] = sha256(output["exercises"])
    output["editorialReceipt"] = {"approvedCandidates": sorted(approved), "workflowVersion": "v36.1"}
    return output


def load(path: str) -> Any:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description="Local V36 editorial catalogue pipeline")
    sub = parser.add_subparsers(dest="command", required=True)
    classify_p = sub.add_parser("classify"); classify_p.add_argument("candidate"); classify_p.add_argument("catalogue")
    validate_p = sub.add_parser("validate"); validate_p.add_argument("exercise")
    draft_p = sub.add_parser("draft"); draft_p.add_argument("base"); draft_p.add_argument("candidates"); draft_p.add_argument("decisions"); draft_p.add_argument("output")
    args = parser.parse_args()
    if args.command == "classify": print(json.dumps(classify(load(args.candidate), load(args.catalogue)["exercises"]), indent=2))
    elif args.command == "validate":
        errors = validate_rich_exercise(load(args.exercise)); print(json.dumps({"valid": not errors, "errors": errors}, indent=2)); raise SystemExit(bool(errors))
    else:
        result = build_release_draft(load(args.base), load(args.candidates), load(args.decisions))
        Path(args.output).write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(json.dumps({"exerciseCount": result["exerciseCount"], "payloadChecksum": result["payloadChecksum"]}))


if __name__ == "__main__": main()
