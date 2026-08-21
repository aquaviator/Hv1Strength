# V37 universal exercise measurement architecture

## Contract

The V37 model separates four concerns that were previously compressed into `LoggedSet` and
`WorkoutTemplateSet`:

1. A canonical metric dictionary defines meaning, value kind, canonical unit, legal source and validation range.
2. An exercise metric profile assigns primary, secondary, optional, derived, prescription, recording and unsupported roles.
3. Equipment capability profiles describe telemetry a device may emit without claiming that every exercise performed on that equipment supplies it.
4. Prescriptions and observations are separate relational records. Observations may own ordered intervals and time-series samples.

The attached *Universal Exercise Measurement Taxonomy* informed the dimensions and boundary cases. Its proposed polymorphic JSON record was deliberately not adopted: values required for filtering, validation, synchronization and analytics remain ordinary columns and rows.

## Semantic boundaries

- `external_load` is resistance applied to an exercise. `assistance` reduces effective demand. They are not interchangeable.
- `mechanical_work` uses joules. `energy` is metabolic expenditure in kilocalories. Neither is inferred from the other without an explicit validated derivation.
- Manufacturer resistance levels and rower drag/damper values retain their original value, unit key, manufacturer and model. A level is never converted to kilograms, watts or another manufacturer's level.
- Canonical storage uses kilograms, metres and seconds. Unit conversion happens only at input/presentation boundaries, and original values/units remain available for device provenance.
- Derived pace and speed require both duration and distance. Unsupported fields are rejected rather than hidden after persistence.

## Room v14/v15

The additive `13 → 14` migration creates:

- `metric_prescription`: target/range values keyed to a stable template-set ID.
- `metric_observation`: one queryable scalar result per logged-set/metric pair, including canonical and original units plus device provenance.
- `metric_segment`: ordered interval or split results.
- `metric_sample`: timestamp-offset time-series samples.

The additive `14 → 15` migration adds ownership, revision, tombstone, acknowledgement and replay metadata to observations. No existing table or column is removed or rewritten. Legacy set columns remain readable and are the compatibility projection for older clients.

## Additive cloud synchronization contract

V37 serializes each observation graph as one atomic schema-14 document at
`users/{humanUserId}/measurementRecords/{observationGlobalId}`. It retains the
stable observation and parent logged-set IDs, revision, tombstone, source unit,
device provenance, and deterministically ordered segment and sample records.
Room remains normalized; downloaded graph replacement is transactional.

Clients predating measurement schema 14 ignore this new collection and retain
their existing `loggedSets` contract. They cannot erase advanced measurements,
and logged-set completion is monotonic during mixed-client convergence. Rules
bind records to the trusted Human owner, freeze parent and stable IDs on update,
and reject revision regression. Deploying those rules remains a separately
authorized production operation.

## Deterministic catalogue audit

Run:

```powershell
python tools/measurement_catalogue_audit.py `
  --source build/v36-publication-readiness/release-exercises.json `
  --output build/v37-measurement-architecture
```

The tool has no networking code. It preserves every governed ID, creates one profile per exercise, rejects supported/unsupported overlap and produces a deterministic candidate, audit report and deferred-review queue. Production catalogue activation is explicitly outside this process.

## UI rule

Exercise details, routine targets and workout logging resolve the governed metric profile before rendering. Legacy values may preserve historical data but may not invent visible capabilities. For example, a treadmill duration/distance profile does not display or accept repetitions or load; assisted strength labels its resistance value as assistance.

## Device and release acceptance

Acceptance must be non-uninstalling and use synthetic disposable data. Capture database counts and stable-reference aggregates before and after; verify conventional strength, assisted/bodyweight, holds, carries, cardio families, intervals, custom exercises, routines, planner links, active/casual logging and summaries. Restore font scale, rotation and connectivity after 1.0×/1.5×/2.0× checks. Production Firebase, identities and user records are never fixture sources.
