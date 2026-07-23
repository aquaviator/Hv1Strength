# Catalogue Principles

## Purpose

The catalogue describes exercises consistently enough for coaches, product teams, and developers to review candidates before any production integration.

## Admission rule

A candidate must satisfy at least one of these conditions:

- commonly programmed by qualified strength or fitness professionals;
- commonly found in commercial gym programmes;
- recognised in established strength, conditioning, or rehabilitation practice;
- regularly searched for or reasonably expected by users;
- provides a useful progression or regression;
- provides a useful equipment-based substitute; or
- has a legitimate sport-specific or clinical application.

Obscurity alone is not sufficient. The review notes should identify the applicable reason.

## Record hierarchy

The conceptual hierarchy is:

`Category → Exercise family → Parent exercise → Canonical exercise → Meaningful variation → Equipment variation → Search alias`

These are classification and reasoning levels, not a command to create a database record at every level.

- A **category** is a broad navigation or reporting area.
- An **exercise family** groups exercises with related intent and mechanics.
- A **parent exercise** is a conceptual anchor used to relate derivatives.
- A **canonical exercise** is a user-recognisable, reviewable candidate record.
- A **meaningful variation** changes an attribute important enough to meet the canonical-record rule.
- An **equipment variation** may be canonical when equipment materially changes mechanics, loading, resistance, stability, purpose, or search expectation.
- A **search alias** is discovery metadata and never a separate canonical record.

`parent_exercise` may therefore name a conceptual parent that is not itself a candidate row.

## Canonical-record rule

A separate canonical exercise normally requires a meaningful difference in at least one of:

- movement mechanics;
- loading profile;
- resistance curve;
- stability demand;
- range of motion;
- technical execution;
- training purpose;
- progression or regression role;
- rehabilitation application; or
- user search expectation.

Tempo, pauses, grip, stance, and minor setup changes do not automatically justify separate records. An exception must explain why it changes programming, safety, progression, rehabilitation use, or durable user expectation.

Branding, informal terminology, and manufacturer terminology are aliases. For example, `Plate Loaded Iso-Lateral Chest Press` may have `Hammer Strength Chest Press`, `Independent Chest Press`, and `Plate Loaded Chest Press` as aliases; the manufacturer name is not canonical.

## Quality principles

The catalogue is deterministic, version controlled, reviewable, auditable, brand-independent, and resistant to duplicate proliferation. Generated entries remain proposals. Clinical claims require qualified review and evidence; neither is fabricated here. Production IDs, coaching content, translations, telemetry, and document versions remain downstream concerns.
