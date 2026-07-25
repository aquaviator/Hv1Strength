# Exercise Catalogue Integration Architecture

Status: implementation-ready design for a Pilot integration slice. This document does not publish or import the 48 Draft records.

## Pilot local staging implementation

Runtime Contract v1 is consumed locally through `PilotCatalogueImporter`. The importer accepts only the
`pilot_staging` channel, verifies the canonical SHA-256 payload before any Room write, validates record
count and canonical references, and stores the snapshot in tables separate from the production
`exercise` table. The checked-in runtime directory is configured as the Android asset source, so the
Pilot fixture has one authoritative repository copy.

An identical `(channel, catalogueVersion, checksum)` import is a deterministic no-op. A changed,
valid Pilot release replaces the prior staging snapshot in one Room transaction while retaining
canonical IDs. This path is deliberately not connected to production search, Compose UI, Kotlin
seed population, or custom-exercise Firestore sync.

## 1. Current application architecture

The Android application is a single Kotlin/Compose module. `Exercise` is both the Room entity and runtime model:

```kotlin
data class Exercise(
    val id: String,
    val name: String,
    val category: String,
    val isCustom: Boolean
)
```

It also carries `VersionedEntity` sync metadata. Room database version 9 seeds 21 global exercises from Kotlin in `StrengthDatabase.populateDatabase`. `WorkoutTemplateExercise.exerciseId`, `LoggedSet.exerciseId`, template JSON, favourites, history, and active-workout state all refer to `Exercise.id`.

`StrengthDao` exposes Room-backed flows and uses replacement inserts. `StrengthRepository` treats non-custom exercises as globally owned and already synced. The sync engine uploads and downloads only user-owned exercises through `users/{humanUserId}/customExercises/{globalId}`. There is no global Firestore catalogue collection, catalogue importer, alias index, or catalogue content-version model.

Exercise search is an in-memory name substring plus one of six category strings. Room remains the offline runtime source of truth. The existing command queue, revision metadata, and Firestore conflict handling serve user-owned mutable data and must not be reused as the authority for read-only official catalogue releases.

## 2. System-of-record boundaries

| Boundary | Authority |
|---|---|
| Authoring | Version-controlled Schema v2 manifest, reference data, validator, review evidence, and immutable source commit |
| Build | One deterministic catalogue transformer operating only on validated, release-eligible records |
| Distribution | Versioned official-catalogue release manifest plus runtime documents/assets |
| Runtime | Room cache populated transactionally from a bundled or downloaded release |
| User data | Existing Room and per-user Firestore paths for favourites, workouts, history, and custom exercises |

Canonical catalogue content is read-only to clients. User-created exercises remain owner-scoped records and never enter the official catalogue namespace.

Target flow:

```text
Schema v2 source and references
  -> validation and governance gate
  -> deterministic runtime release JSON
  -> bundled asset and/or official Firestore distribution
  -> transactional Room import
  -> Room-backed UI and offline search
```

## 3. Identity contract

`canonicalId` is the immutable published identity. It must never change or be reused after publication, and all workouts, programmes, favourites, history, analytics, relationships, media, and future modules refer to it.

The current `catalogue_key` remains an authoring identity and must not silently become a runtime ID. A reviewed release mapping allocates each catalogue key to a canonical ID:

```text
catalogue_key -> canonicalId
```

For an existing seed that represents the same canonical exercise, preserve the existing `Exercise.id` as `canonicalId`. New official records receive a collision-reviewed stable string ID. Display-name changes do not change the ID. A semantic replacement receives a new ID and a `supersedes` edge.

Official Room rows use `canonicalId` as their stable string primary identity; no generated integer is needed because the current primary key is already a string. Custom IDs retain their `custom_...` namespace. A nullable authoring key may be retained in release metadata for traceability, but user data must reference `canonicalId`.

## 4. Publication-state contract

Authoring review state and runtime publication state are separate:

| State | Runtime eligibility |
|---|---|
| Draft | Authoring only; allowed in isolated developer fixtures |
| Architecture/Coaching Reviewed | Staging only; never production-visible |
| Approved | Eligible to enter a release candidate, not automatically visible |
| Published | Included in an authorised production release and discoverable |
| Deprecated/Merged | Hidden from new discovery but retained locally while referenced; replacement metadata may be shown |
| Rejected | Never distributed |

The current 48 are Draft and may only enter an explicitly labelled Pilot fixture or debug-only staging database. The production transformer must fail if a selected record is not Approved and release-authorised.

## 5. Runtime model contract

Do not expand the current entity into a 58-field object. Use compact models:

| Type | Purpose | Key fields | Persistence |
|---|---|---|---|
| `ExerciseSummary` | Lists, workout builder, favourites, lightweight filters | canonical ID, name, category, family, primary pattern, difficulty, custom/official, visibility | Main Room exercise row and Firestore runtime fields |
| `ExerciseDetail` | Detail and coaching screens | setup, execution, errors, safety, range, breathing, media references | One-to-one Room detail row; nested Firestore map |
| `ExerciseMuscleRole` | Muscle filters and role display | canonical ID, muscle slug, role | Room junction rows; Firestore role arrays |
| `ExerciseEquipmentRef` | Equipment filters | canonical ID, equipment slug | Room junction rows; Firestore array |
| `ExerciseAlias` | Offline discovery | canonical ID, alias, normalised alias, type, optional locale | Room alias table; Firestore runtime aliases |
| `ExerciseRelationship` | Variations and reviewed alternatives | source ID, target ID, type | Room edge table; Firestore relationship arrays |
| `CatalogueRelease` | Installed-version tracking | content version, schema version, source commit, checksum, installed time | Room singleton/history and distribution manifest |

Governance details, AI-assistance metadata, review notes, and human-verification evidence remain authoring/release metadata rather than normal UI models.

## 6. Schema v2 mapping

Classification values are:

- **DIRECT**: copied without semantic change;
- **TRANSFORMED**: converted to runtime representation;
- **DERIVED**: produced from reviewed source values;
- **CATALOGUE-ONLY**: retained in authoring/release evidence;
- **NOT CURRENTLY SUPPORTED**: requires the proposed runtime extension.

| Schema v2 field(s) | Classification | Domain / Room / Firestore / usage |
|---|---|---|
| `schema_version` | CATALOGUE-ONLY | Release manifest `schemaVersion`; not exercise UI |
| `catalogue_key` | CATALOGUE-ONLY | Release mapping and audit trace; never an automatic runtime ID |
| `canonical_name` | DIRECT | Summary `name`; exercise row; runtime document; display/search |
| `parent_exercise_key` | TRANSFORMED | Resolve through ID map to `PARENT` edge |
| `variation_type` | TRANSFORMED | Relationship/detail label; controlled string |
| `supersedes_key` | TRANSFORMED | Resolve to `SUPERSEDES` edge; update/deprecation handling |
| `review_status` | CATALOGUE-ONLY | Build gate; release state is emitted separately |
| `review_notes` | CATALOGUE-ONLY | Review evidence only |
| `content_origin` | CATALOGUE-ONLY | Release audit metadata, not normal runtime payload |
| `exercise_family` | DIRECT | Summary/filter string backed by reference slug |
| `temporary_android_category` | TRANSFORMED | Compatibility `category`; current six-tab UI |
| `laterality` | NOT CURRENTLY SUPPORTED | Summary filter after Room extension |
| `compound_or_isolation` | NOT CURRENTLY SUPPORTED | Summary/detail filter |
| `exercise_role` | NOT CURRENTLY SUPPORTED | Structured multi-value lookup |
| `difficulty` | NOT CURRENTLY SUPPORTED | Summary filter; distinct from user experience |
| `technical_complexity` | CATALOGUE-ONLY initially | Detail/filter only when a UI requirement exists |
| `facility_tier` | CATALOGUE-ONLY initially | Future availability filtering |
| movement-pattern fields | NOT CURRENTLY SUPPORTED | Summary primary value and optional structured secondary values |
| joint-action fields | CATALOGUE-ONLY initially | Detail/AI metadata; not needed for Pilot UI |
| support, torso, loading, grip, bench fields | CATALOGUE-ONLY initially | Detail/AI metadata; add only for a concrete feature |
| muscle-role fields | TRANSFORMED | Role junction rows and Firestore arrays |
| `equipment` | TRANSFORMED | Equipment junction rows and Firestore array |
| `attachment_or_implement` | CATALOGUE-ONLY initially | Detail metadata when required |
| `external_load` | CATALOGUE-ONLY initially | Future filtering |
| setup/execution/error/safety/range/breathing fields | DIRECT | `ExerciseDetail`; one-to-one Room row; nested runtime map |
| coaching/clinical review statuses | CATALOGUE-ONLY | Publication gate only |
| training goals and rep styles | CATALOGUE-ONLY initially | Future programming/AI features |
| `loadability` | CATALOGUE-ONLY initially | Future programming feature |
| `substitution_group` | CATALOGUE-ONLY | Search/planning aid; not a runtime relationship |
| progression/regression keys | TRANSFORMED | Resolve to typed directed edges |
| contraindication flags | CATALOGUE-ONLY | Do not expose without approved clinical UX |
| search aliases, regional aliases, abbreviations, legacy names | TRANSFORMED | Typed aliases and search tokens |
| manufacturer aliases | CATALOGUE-ONLY initially | Include only after product/legal review |
| search keywords | TRANSFORMED | Normalised search tokens |
| AI tasks, flags, suitability tags | CATALOGUE-ONLY | Governance and audit |
| `source_provenance` | CATALOGUE-ONLY | Release evidence |
| `human_verified_fields` | CATALOGUE-ONLY | Approval evidence |

Runtime-only fields include `canonicalId`, publication visibility, catalogue content version, checksum/revision, media references, installed timestamps, custom ownership, local sync state, favourites, and user-history references.

## 7. Room storage

Use a hybrid schema, not one wide table and not full reference-data normalisation:

- evolve the current `exercise` row into the summary/query surface while preserving every existing ID;
- add `exercise_detail` for long-form content;
- add `exercise_alias` with an index on normalised alias;
- add `exercise_muscle` and `exercise_equipment` junction tables for filters;
- add `exercise_relationship` for typed edges;
- add an FTS table or maintained token column for offline search;
- add `catalogue_release` for installed content metadata.

Use controlled-value slugs as stored strings. Do not use Kotlin enums for expandable catalogue vocabularies. Stable application concepts such as relationship type and publication visibility may use closed Kotlin types with explicit unknown-value handling.

All release imports run in one Room transaction. A failed validation, parse, referential check, or write leaves the previously installed release intact.

## 8. Firestore distribution

Keep the existing `users/{userId}/customExercises` flow unchanged.

Proposed read-only official distribution:

```text
catalogueReleases/{contentVersion}
officialExercises/{canonicalId}
```

The release manifest contains schema version, content version, source commit, checksum, publication channel, published time, changed IDs, deprecated IDs, and minimum importer contract version.

An official exercise document contains only runtime fields: canonical ID, display identity, compatibility category, selected taxonomy/filter data, aliases/search tokens, detail content, typed relationships, media IDs, publication visibility, content version, and timestamps. Authoring notes and AI governance evidence stay in Git/release evidence.

Clients may read published official content but cannot write or approve it. Publication is performed by trusted build/release infrastructure, never by an Android client.

## 9. Search and aliases

Offline search is authoritative. Build a normalised token index from canonical name, approved aliases, abbreviations, search keywords, muscles, equipment, movement patterns, and family.

Use case-folding, punctuation/diacritic normalisation, and token prefixes. At the expected scale, Room FTS or indexed local tokens are sufficient; an external search service is unnecessary.

Aliases resolve to one canonical ID and never create exercise rows or alternative IDs. Minimal alias shape:

```text
canonicalId, alias, normalisedAlias, aliasType, locale?
```

Locale defaults to the release locale and may remain absent in the Pilot. Global collision validation occurs before export.

## 10. Relationships

Persist directed typed edges:

- `PARENT`: variation belongs to a conceptual base;
- `PROGRESSION`: reviewed directed programming progression;
- `REGRESSION`: reviewed directed programming regression;
- `SUPERSEDES`: a new identity replaces a deprecated identity.

Do not infer difficulty from parentage, equipment changes, or substitution groups. UI labels should be “Variations,” “Progressions,” “Regressions,” and “Replaced by” only when the matching explicit edge exists. Generic related/substitution UI requires a separately reviewed relationship type.

## 11. Media

Exercise records reference stable media IDs, not storage URLs. A separate media manifest maps media ID to asset kind, storage path, checksum, licence, locale, dimensions/duration, and lifecycle state. Media absence must not change canonical identity.

## 12. Offline-first and updates

Lifecycle:

1. ship an authorised bundled release asset for first-run offline use;
2. import it transactionally when no equal/newer compatible release is installed;
3. UI observes Room only;
4. fetch the latest release manifest when online;
5. download and checksum a full snapshot for the Pilot;
6. validate the complete snapshot and references;
7. apply an idempotent upsert/deprecation transaction;
8. rebuild affected search data;
9. record the installed release only after commit.

Use full versioned snapshots initially. At 2,000 records they remain operationally simple and small enough for infrequent catalogue releases. The manifest may later provide deltas without changing the canonical model.

Never hard-delete an official exercise referenced by a workout. Deprecated records are hidden from discovery but retained for historical joins. A display rename updates the same ID. A semantic replacement creates a new ID and `SUPERSEDES` edge.

## 13. Catalogue versioning

Keep four versions distinct:

- Schema version: source contract, currently `2.0`;
- Catalogue content version: immutable release identifier, recommended `YYYY.MM.patch`;
- Importer contract version: runtime JSON/Room mapping contract;
- Android/Room versions: application binary and database migrations.

Store the installed catalogue content version and source commit. Workout history need not copy the whole catalogue version when it holds an immutable canonical ID, but exports and analytics events should include the active content version for reproducibility.

## 14. Staging and importer

Lifecycle:

```text
AI/imported candidate
  -> Git Schema v2 Draft and validation
  -> isolated staging fixture
  -> independent review and approval evidence
  -> approved release selection
  -> deterministic runtime JSON
  -> trusted publication
  -> bundled/downloaded client import
```

The transformer belongs beside the catalogue validation tooling and is the only Schema v2-to-runtime mapping implementation. Room and Firestore consume the same generated runtime contract. It must:

- invoke or require successful Schema v2 validation;
- enforce the selected publication channel and governance gate;
- resolve all IDs and relationships through an explicit mapping;
- reject unsupported controlled values and alias collisions;
- sort output deterministically;
- emit schema/content/contract versions, source commit, and checksum;
- fail loudly rather than drop fields silently.

## 15. Migration impact

| Surface | Impact | Reuse / change |
|---|---|---|
| Domain models | MODERATE | Preserve current lightweight use; introduce summary/detail contracts |
| Room schema | MODERATE | Preserve exercise IDs; add detail, alias, role, edge, and release structures |
| DAOs | MODERATE | Reuse flows; add transactional import, filters, FTS, and relationship queries |
| Repository | MODERATE | Keep Room authority; add read-only catalogue-release repository |
| Firestore DTOs | MODERATE | New official read-only DTOs; custom-exercise DTO remains unchanged |
| Sync queue | SMALL | Do not queue official records; add separate release fetch path |
| Search | MODERATE | Replace name-only filtering with Room-backed token/FTS search |
| Workout builder | SMALL | Continue using immutable string IDs; consume summaries |
| Exercise detail | MODERATE | Read optional detail structure |
| Seed data | MODERATE | Replace Kotlin literals only after importer proves parity |
| Tests | MODERATE | Add transformation, migration, Room, search, update, and governance coverage |

Existing workout/history references are reusable because they already use string exercise IDs. The main identity task is a reviewed mapping from each catalogue candidate to an existing or newly allocated canonical ID.

## 16. Scalability

| Scale | Assessment |
|---:|---|
| 48 | Trivial; ideal contract fixture |
| 400 | Full snapshot and Room FTS remain simple |
| 1,000 | Still suitable for Room; avoid per-document startup reads |
| 2,000 | Still modest locally; use bundled/snapshot transport, indexes, paging, and transactional updates |

The architectural choices do not change across these sizes. Firestore cost and latency argue against fetching every document on every startup; release manifests and cached snapshots avoid that. Compose should render paged or lazy Room results, not retain enriched detail objects for every row.

## 17. Pilot integration slice

Use eight exact Draft records in a debug/test-only fixture:

1. Push-Up — bodyweight;
2. Barbell Bench Press — barbell and bench;
3. Dumbbell Goblet Squat — dumbbell;
4. Seated Cable Row — cable and alias search;
5. Selectorized Chest Press — machine;
6. Bulgarian Split Squat — unilateral;
7. Plank — core/isometric;
8. Farmer's Carry — carry, distance, and non-six-family metadata pressure.

The slice tests identity allocation, category transformation, equipment and muscle roles, aliases, detail content, relationships, search, and unsupported-category handling without claiming production approval.

## 18. Test strategy

Before any production import:

- transformer golden tests for all eight records;
- rejection tests for Draft records in a production channel;
- canonical-ID mapping stability and collision tests;
- required-field and relationship-resolution tests;
- deterministic ordering/checksum tests;
- alias and keyword search tests (`RDL`, `cable row`, `quads`, `side delts`, equipment);
- Room migration and transactional rollback tests;
- update tests proving a rename upserts rather than duplicates;
- deprecation tests proving saved workout references still resolve;
- full snapshot idempotency tests;
- custom-exercise separation tests;
- Firestore DTO tests excluding authoring-only governance fields.

## 19. Security and trust boundary

Generated or imported data is untrusted until validated and approved. Only trusted CI/release infrastructure can transform and publish an official release. Firestore rules must make the official namespace client-read-only and keep approval/staging writes server-side. Draft staging must use a separate collection/channel and must never be queried by production clients.

Existing owner-scoped custom exercises remain mutable user data. Official catalogue rows are not uploaded through the command queue, cannot be approved by clients, and cannot overwrite custom IDs.

## 20. Blockers and deferred work

Integration planning is unblocked. Production publication remains blocked by independent human approval of the current Draft records and by the absence of reviewed canonical-ID allocations.

Deferred until implementation authority:

- canonical-ID mapping for all candidates and legacy seed reconciliation;
- runtime JSON schema and transformer;
- Room schema/migrations and exported Room schemas;
- official Firestore collections and security rules;
- release signing/checksum policy;
- media manifest;
- localisation and manufacturer-alias policy;
- production publication of any catalogue record.

## 21. Exact next implementation slice

Sprint 6 should implement only the contract fixture and deterministic transformation boundary:

1. define a versioned runtime JSON schema and Kotlin-neutral fixture contract;
2. add an explicit eight-record `catalogue_key -> canonicalId` Pilot mapping without changing production seeds;
3. implement a deterministic Python transformer beside the validator;
4. enforce a `pilot_staging` channel that permits these Draft fixtures and a `production` channel that rejects them;
5. generate one checked-in staging JSON fixture for the eight records;
6. add transformation, identity, collision, governance-gate, relationship-resolution, deterministic-output, and alias-token tests;
7. do not change Room, Firestore, Android models, seed data, UI, or security rules.

This slice proves the contract before any database migration or runtime import.

## 22. Implemented Sprint 6 contract

The executable Pilot contract is:

- JSON Schema: `catalogue/runtime/runtime-catalogue-contract-v1.schema.json`;
- canonical ID mapping: `catalogue/runtime/canonical-id-map-v1.json`;
- deterministic transformer: `tools/catalogue/build_runtime_catalogue.py`;
- generated staging fixture: `catalogue/runtime/pilot-staging-v1.json`;
- regression coverage: `tests/test_build_runtime_catalogue.py`.

Generate the fixture with:

```powershell
.\.venv\Scripts\python.exe tools/catalogue/build_runtime_catalogue.py `
  --channel pilot_staging `
  --output catalogue/runtime/pilot-staging-v1.json
```

Verify byte-for-byte reproducibility with the same command plus `--check`.

Runtime contract version 1 exports canonical identity, selected classification slugs, typed aliases and keywords, muscle roles, equipment and attachments, coaching/detail content, and canonical-ID relationships. It excludes review notes, governance statuses and flags, AI-assistance metadata, authoring provenance, human-verification metadata, programming metadata not yet consumed at runtime, and other authoring-only fields identified in the mapping table above.

The release checksum is lowercase SHA-256 over canonical UTF-8 JSON containing every envelope field except `checksum`. Canonical JSON sorts object keys, uses compact separators, preserves deterministic array ordering, and contains no generated timestamp or machine-specific path.

`pilot_staging` accepts only the eight configured Draft fixtures. `production` applies the documented approval gate and rejects the current records. Valid relationships to records outside this isolated mapping are omitted; unknown source targets fail validation. A future full release must map every exported relationship target.

Sprint 7 may consume only the checked-in staging fixture in a debug/test-isolated path. It should implement parser/contract models and Room staging persistence tests without replacing Kotlin seeds, exposing Draft records in production UI, or changing the existing custom-exercise Firestore sync path.
