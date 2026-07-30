# Human Exercise Measurement Semantics v1

## Purpose

Measurement semantics define how an exercise may legitimately be recorded. They are
authored and governed independently from classification, anatomy, equipment and app
presentation. Applications choose controls and formatting; they do not infer the
logging contract from movement pattern, force direction or equipment.

## Normalized authoring model

The authoring contract is split across three deterministic CSV tables:

- `measurement-modes-v1.csv` declares stable mode IDs, the single default mode,
  load semantics and contract compatibility.
- `measurement-mode-fields-v1.csv` declares each required or optional recorded
  measurement and its canonical unit.
- `measurement-mode-derived-v1.csv` declares metrics that may be derived from the
  recorded measurements.

This normalized representation permits multiple modes per exercise without embedding
an unvalidated JSON or delimiter-based sub-schema in the main exercise manifest.

## Governed vocabularies

- `measurements.csv`
- `measurement-units.csv`
- `load-semantics.csv`
- `derived-metrics.csv`

Operational measurements in the Pilot are repetitions, load, duration, distance, RPE
and assistance. Calories, power, cadence, heart rate, resistance, speed, pace, count
and vertical distance are reserved governed vocabulary for later consumers. Their
presence in vocabulary does not make them operational inputs.

Pace and speed are initially derived metrics. Both require distance and duration.

## Canonical units

| Semantic value | Canonical unit |
|---|---|
| Repetitions / count | `count` |
| Load / assistance | `kilograms` |
| Duration | `seconds` |
| Distance / vertical distance | `metres` |
| RPE | `rpe_scale` |
| Speed | `metres_per_second` |
| Pace | `seconds_per_metre` |

Kilograms, metres and seconds are storage/interchange semantics. User display in
pounds, kilometres or formatted pace remains an application concern.

## Load semantics

- `external_load`: recorded mass is the external resistance moved or applied.
- `added_load`: recorded mass is additional to bodyweight.
- `assistance`: recorded mass-equivalent assistance reduces effective resistance.
- `bodyweight`: body mass is the resistance; it is not zero load.
- `none`: the mode has no load interpretation.

Validators reject incompatible measurement/load combinations.

## Runtime contract

Runtime Catalogue Contract v2 adds `measurement_modes` to each exercise. Every mode
contains:

- stable `mode_id`;
- `is_default`;
- required and optional `{measurement, unit}` entries;
- `load_semantics`;
- `derived_metrics`;
- `measurement_schema_version`.

Exactly one mode is default per exercise. Runtime v1 remains immutable and available
for historical compatibility. Runtime v2 is used by the isolated Pilot importer.

## Lifecycle and identity

Measurement-mode changes are metadata changes to the same exercise identity when the
underlying exercise remains semantically the same. They do not allocate a new
canonical ID.

A rename also retains the canonical ID. A genuinely different exercise must receive a
new canonical ID and may declare a reviewed supersession relationship. Published IDs
must never be reused.

Schema v2 already governs exercise editorial lifecycle through `review_status`,
retired keys and `supersedes_key`. Pilot runtime visibility remains controlled by the
release channel. Per-record revision and publication timestamps are deferred until a
trusted production release manifest exists; inventing local timestamps in authoring
CSV would not establish publication authority.

## Activity, modality and equipment

Measurement modes do not collapse activity into equipment. Running and treadmill,
cycling and stationary bike, and rowing and row erg remain distinct concepts or
relationships in the wider ontology. This sprint does not redesign that relationship
model.

## Line endings

Generated runtime JSON uses UTF-8 with LF line endings and exactly one final LF.
Generated files must be checked using the transformer `--check` mode. Git attributes
pin runtime JSON and catalogue CSV files to LF so Windows checkout settings cannot
invalidate byte reproducibility.

## Custom exercises

The runtime shape is suitable for future user-owned custom exercises. Custom records
remain outside official publication governance and can never modify an official
canonical definition.
