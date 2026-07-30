# Phase 2 Knowledge Expansion

## Scope and baseline

This sprint expands governed candidate knowledge only. It does not publish a
catalogue release, allocate production identities, connect Firestore, or wire
catalogue data into Human Strength or Human HIIT.

The pre-change 48-record inventory is preserved in
`PHASE-2-BASELINE-INVENTORY.csv`. It records identity allocation,
classification, movement, equipment, review state, measurement coverage,
modes, and obvious gaps for every baseline record.

Baseline findings:

- 48 Schema v2 records, all Draft.
- 9 records had explicit measurement semantics; 39 did not.
- 8 records had stable canonical IDs; 40 did not.
- 0 records were publication-ready.
- Conditioning contained only Sled Push and Backward Sled Drag.
- There were no governed continuous-cardio or mobility records.
- Pull-Up already demonstrated distinct bodyweight, added-load, and assistance
  semantics.

## Live Human Strength reconciliation

No identity is allocated by this table. “Review” includes naming, equipment
scope, coaching, and publication review.

| Live seed | Candidate-Data result | Catalogue key | Canonical ID | Decision |
|---|---|---|---|---|
| Bench Press | Governed equivalent; barbell is explicit | `barbell_bench_press` | `bench_press` | Existing identity protected |
| Incline Dumbbell Press | Governed naming equivalent | `incline_dumbbell_bench_press` | — | Needs review |
| Chest Fly | Plausible equipment-specific match | `cable_chest_fly` | — | Needs review |
| Deadlift | Exact governed match | `deadlift` | — | Needs review |
| Pull Up | Exact identity; governed punctuation differs | `pull_up` | — | Needs review |
| Barbell Row | Plausible naming match | `barbell_bent_over_row` | — | Needs review |
| Lat Pulldown | Exact governed match | `lat_pulldown` | — | Needs review |
| Barbell Squat | Plausible match to Back Squat | `back_squat` | — | Needs review |
| Romanian Deadlift | Exact governed match | `romanian_deadlift` | — | Needs review |
| Leg Press | Exact governed match | `leg_press` | — | Needs review |
| Calf Raise | Missing | — | — | Defer pending precise identity |
| Overhead Press | Exact governed match | `overhead_press` | — | Needs review |
| Lateral Raise | Exact governed match | `lateral_raise` | — | Needs review |
| Rear Delt Fly | Missing | — | — | Defer pending equipment scope |
| Bicep Curl | Ambiguous between barbell and dumbbell records | `barbell_curl` / `dumbbell_curl` | — | Needs review |
| Tricep Pushdown | Governed naming equivalent | `triceps_pushdown` | — | Needs review |
| Hammer Curl | Exact governed match | `hammer_curl` | — | Needs review |
| Skull Crusher | Missing | — | — | Defer |
| Hanging Leg Raise | Missing | — | — | Defer |
| Plank | Exact governed match | `plank` | `plank` | Existing identity protected |
| Abdominal Crunch | Plausible equipment-specific match | `cable_crunch` | — | Needs review |

## Knowledge added

Five Draft records were added through Schema v2 authoring:

- `running`: equipment-independent running activity.
- `treadmill_run`: equipment variation of `running`.
- `row_erg`: rowing-ergometer activity; broader Rowing identity remains
  deferred rather than implied.
- `burpee`: bodyweight conditioning identity with repetitions and timed-work
  modes. Recovery, rounds, and protocol timing are intentionally absent.
- `downward_dog`: a real governed mobility record with explicit timed-hold
  semantics.

Every safely interpretable record now has explicit measurement semantics.
Existing loaded lifts use repetitions plus external load with optional RPE.
Bodyweight movements use repetitions or duration as declared. Carries and sled
work use load plus distance with optional duration and RPE.

## Activity and equipment

Activity identity and equipment implementation remain distinct:

- Running is the activity anchor; Treadmill Run is an equipment variation.
- Row Erg is an ergometer activity. On-water Rowing is not silently treated as
  the same identity.
- Cycling versus Stationary Bike and skiing motion versus Ski Erg remain
  deferred until activity anchors can be reviewed alongside implementation
  records.

Equipment is validated against controlled reference values. `Treadmill` is the
only new equipment term. No duplicate or synonymous governed term was found.
Equipment strings do not determine measurement behavior.

## Laterality and count

The current `laterality` vocabulary already distinguishes bilateral,
unilateral, alternating, and asymmetrical records. Measurement values describe
the completed exercise record; no UI-oriented left/right field is needed yet.
Bulgarian Split Squat and unilateral rows remain explicit identities with
catalogue-declared modes. A future need to persist separate side results would
justify an additive execution-basis concept, but this dataset does not.

`reps` remains the operational measure for exercise repetitions, including
Burpees. `count` remains governed but future-only for events that are not
semantically repetitions, such as rope rotations, steps, lengths, or machine
strokes. Jump Rope was therefore deferred rather than forcing premature count
semantics.

## Metric taxonomy

| Metric | Classification | Phase 2 decision |
|---|---|---|
| pace | Derived metric | Active only when distance and duration are present |
| speed | Derived metric | Active only when distance and duration are present |
| power | Performance telemetry | Governed, future-only |
| cadence | Performance telemetry | Governed, future-only |
| heart rate | Sensor telemetry | Governed, future-only; not performance input |
| calories | Estimated telemetry | Governed, future-only |
| resistance | Equipment state | Governed, future-only pending machine semantics |
| vertical distance | Direct recorded measurement | Governed, future-only |

Running, Treadmill Run, and Row Erg derive pace and speed only in their combined
distance-and-duration mode. No derived result is authored without its required
inputs.

## HIIT protocol boundary

The catalogue owns Burpee identity, movement semantics, equipment, and valid
repetition or duration recording. A future Human HIIT protocol owns work and
recovery duration, rounds, blocks, sequencing, transitions, and intensity
prescription. No protocol data is embedded in the Burpee record.

## Relationships

`parent_exercise_key` plus `Equipment Variation` expresses Treadmill Run’s
relationship to Running without using supersession. Existing aliases remain
search terms, not separate identities. Substitution groups remain programming
hints; they do not imply identity. Progression/regression and supersession keep
their existing meanings. Assisted Pull-Up remains a measurement mode of
Pull-Up, not a replacement identity.

## Canonical-ID readiness

- **Ready for canonical ID:** none automatically. All candidates remain Draft,
  so no new allocation crosses the governance boundary.
- **Needs review:** the 17 non-missing live-seed reconciliation candidates,
  plus Running, Treadmill Run, Row Erg, Burpee, and Downward Dog.
- **Defer:** Calf Raise, Rear Delt Fly, Skull Crusher, Hanging Leg Raise,
  ambiguous generic Bicep Curl, broader Rowing, Cycling/Stationary Bike,
  skiing/Ski Erg, Walking/Treadmill Walk, Air Bike, Elliptical, Stair Climber,
  Mountain Climber, Jump Rope, and additional mobility records.

The existing eight canonical mappings are unchanged.

## Data-quality result

After expansion:

- total records: 53
- records with measurement semantics: 53
- records without measurement semantics: 0
- records with canonical IDs: 8
- records without canonical IDs: 45
- publication-ready records: 0
- review-required Draft records: 53
- conditioning-family records: 6
- continuous-cardio records: 3
- governed mobility records: 1
- existing loaded-strength records with explicit modes: 33
- bodyweight/assisted family coverage includes Push-Up, Pull-Up, Walking Lunge,
  Glute Bridge, Plank, Side Plank, Dead Bug, Bird Dog, Bulgarian Split Squat,
  Burpee, and Downward Dog

## Production-bootstrap blockers

The catalogue is not ready for a production bootstrap. The remaining blockers
are:

1. resolve the four missing live Strength identities;
2. resolve ambiguous generic seed names against equipment-specific records;
3. complete human identity, anatomy, coaching, alias, and source review;
4. allocate stable canonical IDs only after approval;
5. reach full coverage for the required production seed set;
6. verify relationship integrity after those identities are resolved; and
7. create a reviewed production-channel release separately.

Runtime contract v2 remains sufficient. The Android staging importer requires
no change because the new records use the existing mode, field, unit, load, and
derived-metric structures.
