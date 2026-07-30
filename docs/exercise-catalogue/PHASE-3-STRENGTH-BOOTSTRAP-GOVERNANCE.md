# Phase 3 Strength Bootstrap Identity Governance

## Authority and scope

The audited legacy authority is the 21-entry
`StrengthDatabase.populateDatabase.defaultExercises` list in the live
Version3 source. Each entry has a stable seed ID, display name, category, and
matching `globalId`. Version3 is read-only in this sprint.

The governed bridge is
`catalogue/mapping/strength-bootstrap-mapping-v1.json`. A future migration must
look up the stable `legacy_seed_id`; display names are evidence and presentation
metadata, not runtime keys.

## Reconciliation result

| Legacy ID | Legacy name | Candidate key | Relationship | Status |
|---|---|---|---|---|
| `bench_press` | Bench Press | `barbell_bench_press` | POSSIBLE_MATCH_REQUIRES_REVIEW | Canonical allocated; equipment review required |
| `incline_db_press` | Incline Dumbbell Press | `incline_dumbbell_bench_press` | NAMING_EQUIVALENT | Human/source review |
| `chest_fly` | Chest Fly | — | AMBIGUOUS_LEGACY_IDENTITY | Blocked |
| `deadlift` | Deadlift | `deadlift` | EXACT_IDENTITY | Human/source review |
| `pull_up` | Pull Up | `pull_up` | NAMING_EQUIVALENT | Human/source review |
| `barbell_row` | Barbell Row | `barbell_bent_over_row` | NAMING_EQUIVALENT | Posture/source review |
| `lat_pulldown` | Lat Pulldown | `lat_pulldown` | EXACT_IDENTITY | Human/source review |
| `squat` | Barbell Squat | `back_squat` | POSSIBLE_MATCH_REQUIRES_REVIEW | Bar position blocked |
| `romanian_deadlift` | Romanian Deadlift | `romanian_deadlift` | EXACT_IDENTITY | Human/source review |
| `leg_press` | Leg Press | `leg_press` | EXACT_IDENTITY | Human/source review |
| `calf_raise` | Calf Raise | `standing_calf_raise` | POSSIBLE_MATCH_REQUIRES_REVIEW | Posture/equipment blocked |
| `overhead_press` | Overhead Press | `overhead_press` | EXACT_IDENTITY | Human/source review |
| `lateral_raise` | Lateral Raise | `lateral_raise` | EQUIPMENT_SPECIFIC_EQUIVALENT | Equipment review |
| `rear_delt_fly` | Rear Delt Fly | `dumbbell_rear_delt_fly` | POSSIBLE_MATCH_REQUIRES_REVIEW | Equipment blocked |
| `bicep_curl` | Bicep Curl | — | AMBIGUOUS_LEGACY_IDENTITY | Blocked |
| `tricep_pushdown` | Tricep Pushdown | `triceps_pushdown` | NAMING_EQUIVALENT | Human/source review |
| `hammer_curl` | Hammer Curl | `hammer_curl` | EQUIPMENT_SPECIFIC_EQUIVALENT | Equipment review |
| `skull_crusher` | Skull Crusher | `barbell_skull_crusher` | POSSIBLE_MATCH_REQUIRES_REVIEW | Equipment blocked |
| `hanging_leg_raise` | Hanging Leg Raise | `hanging_leg_raise` | EXACT_IDENTITY | Human/source review |
| `plank` | Plank | `plank` | EXACT_IDENTITY | Canonical allocated; governance review required |
| `crunch` | Abdominal Crunch | — | AMBIGUOUS_LEGACY_IDENTITY | Blocked |

Totals are 7 exact identities, 4 naming equivalents, 2 equipment-specific
equivalents, 5 possible matches requiring review, and 3 unresolved ambiguous
legacy identities.

## Missing coverage resolved

Four precise Draft records were added:

- `standing_calf_raise`
- `dumbbell_rear_delt_fly`
- `barbell_skull_crusher`
- `hanging_leg_raise`

Each has governed taxonomy, anatomy, equipment, measurement modes, review
flags, and explicit pending provenance. The first three resolve catalogue
coverage but do not resolve the generic legacy identity automatically.

## Generic and equipment-specific identity policy

Use an equipment-specific identity when the equipment materially defines
execution and is evidenced by the source identity. Use a generic movement or
activity anchor only when the catalogue intentionally governs the shared
identity independently of implementation. Use a legacy compatibility identity
only when historical meaning can be stated accurately without inventing
equipment, mechanics, or anatomy.

No legacy compatibility records were created in this sprint. The live database
contains insufficient evidence to define truthful generic Chest Fly, Bicep
Curl, or Abdominal Crunch records. Creating placeholder exercise facts would
replace an explicit ambiguity with fabricated semantics.

Aliases support discovery and presentation. They are not migration mappings.
For example, Pull Up and Pull-Up can safely differ as presentation strings.
Bicep Curl cannot become an alias of Dumbbell Curl because that would assert
equipment that the historical record does not contain.

## Canonical identity ledger

`canonical-id-map-v1.json` remains the identity ledger. Allocation is:

1. review a stable catalogue identity and its source evidence;
2. append one unique deterministic canonical ID entry;
3. never delete, recycle, or regenerate an allocated ID;
4. preserve the ID when display names or aliases change;
5. allocate a new ID when semantics represent a replacement identity; and
6. record retirement or supersession without transferring the old ID.

No new canonical IDs were allocated. Existing `bench_press` and `plank`
allocations remain protected. Neither allocation makes its mapping Approved.

## Review and source evidence

Production readiness requires reviewed evidence for identity, canonical naming,
taxonomy, anatomy, equipment, measurement modes, load semantics, aliases,
relationships, safety/coaching metadata where required, and source provenance.
The existing records cite internal pilot briefs or explicitly pending Phase 2/3
authorship. Human-verified fields are empty and no external research sources
are recorded. Therefore all records remain Draft and no mapping is Approved.

## Measurement and historical-data safety

All 18 entries with a candidate catalogue key have explicit governed modes.
Bench Press uses repetitions plus external load; Pull-Up has bodyweight,
added-load, and assistance modes; Plank uses duration. The four new precise
records also have explicit modes.

Chest Fly, Bicep Curl, and Abdominal Crunch remain unresolved and therefore
cannot yet receive authoritative bootstrap semantics. This is safer than
assigning historical sets to narrower cable or dumbbell identities.

A future migration must add canonical references without rewriting the meaning
of old templates, sessions, sets, history, progression, or custom exercises.
It must be repeatable and keyed by legacy seed ID. Unresolved or non-Approved
entries must stop migration rather than fall back to display-name matching.

## Readiness

- legacy seeds: 21
- exact identities: 7
- naming equivalents: 4
- equipment-specific equivalents: 2
- possible matches requiring review: 5
- ambiguous identities without a candidate: 3
- formerly missing catalogue areas covered: 4
- canonical IDs already allocated: 2
- new canonical IDs allocated: 0
- mappings Approved: 0
- mappings requiring human review: 12
- canonical-allocated but review-required: 2
- mappings blocked by ambiguity: 7

**Verdict: not ready for a production bootstrap snapshot.**

The exact blockers are the three unresolved generic identities, four additional
implementation ambiguities, missing external/source evidence, incomplete human
review, 19 missing canonical allocations, and zero Approved catalogue records
or mappings.

Runtime contract v2 remains sufficient and unchanged. No Android importer,
Firestore, live Strength, or Human HIIT change is required for this governance
stage.
