# Human Strength Catalogue — Current-State Audit

Audit date: 2026-07-27  
Audited worktree: `C:\Projects\StudioProjects\Hv1Strength-Catalogue`  
Scope: forensic current state only; no implementation, branch switch, merge, catalogue regeneration, migration, or deployment.

## Executive summary

`Candidate-Data` is a clean, remote-synchronised historical worktree at `373ef91`. It is 25 commits ahead and 10 commits behind current `origin/main`, and 25 ahead/11 behind `origin/Version3`; both comparisons share merge-base `b9e4dcf`. The catalogue consists of a 10-row Schema v1 demonstration CSV, a 48-row Schema v2 authoring CSV, controlled reference CSVs, an eight-entry canonical-ID map, a Runtime Contract v1 JSON Schema, and an eight-exercise pilot staging release.

The Android work is an isolated staging integration. Runtime JSON is packaged as an asset and can be parsed/imported into five catalogue staging tables, but no production startup, repository, UI, search, exercise picker, or workout-logging path invokes the importer or reads the staging DAO. Production still uses the older `exercise` table and its 21 seeded exercises. Firestore official-catalogue distribution is design-only.

Python catalogue tests pass (51/51). The runtime fixture contains the expected semantic output and checksum, but the transformer's byte-for-byte `--check` fails because the tracked file has different line endings from freshly serialised LF output. Android catalogue tests could not run because the checked-in `gradle/wrapper/gradle-wrapper.jar` is corrupt.

## 1. Git state

Preflight results after `git fetch origin`:

- Branch: `Candidate-Data` (the expected branch; no branch was switched).
- HEAD: `373ef91d1ba69010cafde9971a2133703352942a` — `test: prove catalogue import transaction rollback`.
- `origin/Candidate-Data`: the same commit.
- Candidate tracking state: 0 ahead, 0 behind.
- Initial working tree: clean.
- Remote: `origin https://github.com/aquaviator/Hv1Strength.git` for fetch and push.
- Fetch changed only the local remote-tracking ref for `origin/main`, from `3202a7d` to `1e26a1f`.
- `origin/main` HEAD: `1e26a1f refactor: update brand assets and launcher icon`.
- Relationship to `origin/main`: Candidate is 25 commits ahead and 10 behind; merge-base `b9e4dcf2ee36822042ee12e2d0e5e93f6ed364d4`.
- `origin/Version3` HEAD: `f94c5b6 Merge remote-tracking branch 'origin/main' into Version3`.
- Relationship to `origin/Version3`: Candidate is 25 commits ahead and 11 behind; the same merge-base `b9e4dcf`.
- No divergence was reconciled.

The latest 25 Candidate commits are headed by the rollback test (`373ef91`), catalogue migration/integration test (`88794bf`), Android pilot integration (`328f6df`), runtime transformer (`442abfc`), integration architecture (`618dd1e`), then the Schema v2 catalogue/taxonomy batches.

## 2. Folder tree

```text
Hv1Strength-Catalogue/
├── catalogue/
│   ├── candidate-manifest.csv                 # Schema v1 demo source (10)
│   ├── candidate-manifest-v2.csv              # Schema v2 authoring source (48)
│   ├── catalogue-audit.md                      # old validator output
│   ├── families/                               # 13 family placeholders + README
│   ├── reference/
│   │   ├── controlled-values.json              # v1 controlled values
│   │   ├── equipment.csv                       # v1 equipment
│   │   └── v2/
│   │       ├── schema-version.json
│   │       ├── retired-keys.csv
│   │       └── 30 controlled-vocabulary CSVs
│   └── runtime/
│       ├── canonical-id-map-v1.json
│       ├── runtime-catalogue-contract-v1.schema.json
│       └── pilot-staging-v1.json
├── tools/catalogue/
│   ├── validate_catalogue.py
│   └── build_runtime_catalogue.py
├── tests/
│   ├── test_validate_catalogue.py
│   ├── test_validate_catalogue_v2.py
│   └── test_build_runtime_catalogue.py
├── app/src/main/
│   ├── java/com/example/data/
│   │   ├── Entities.kt                         # production Exercise entity
│   │   ├── StrengthDao.kt
│   │   ├── StrengthDatabase.kt                 # Room v10 + migrations
│   │   ├── StrengthRepository.kt
│   │   └── catalogue/
│   │       ├── RuntimeCatalogueModels.kt
│   │       ├── CatalogueStagingEntities.kt
│   │       ├── CatalogueStagingDao.kt
│   │       └── PilotCatalogueImporter.kt
│   └── (assets source points to catalogue/runtime)
├── app/src/test/java/com/example/
│   └── PilotCatalogueImporterTest.kt
├── docs/
│   ├── exercise-catalogue-integration.md
│   └── exercise-catalogue/                     # 17 catalogue documents + inventory CSV
├── firestore.rules
├── firestore.indexes.json
├── firebase.json
└── app/build.gradle.kts
```

No standalone authoring JSON Schema exists for v1 or v2. Their executable schemas are Python column/validation constants plus reference files; `HUMAN-EXERCISE-SCHEMA-v2.md` is descriptive. The only JSON Schema is the runtime contract.

## 3. Catalogue source files and record counts

| File/group | Actual count | Responsibility |
|---|---:|---|
| `candidate-manifest.csv` | 10 records | Schema v1 demonstration candidates; CSV |
| `candidate-manifest-v2.csv` | 48 records | Schema v2 governed authoring pilot; CSV |
| v2 families represented | 8 | Arms 6; Back 6; Chest 6; Conditioning 2; Core 10; Hinge 6; Shoulders 6; Squat/legs 6 |
| `reference/v2/*.csv` | 32 files including retired keys | Controlled/expandable vocabularies and lifecycle status |
| `canonical-id-map-v1.json` | 8 mappings | Selected pilot source key to immutable runtime identity |
| `pilot-staging-v1.json` | 8 exercises | Runtime staging release |

The 48-exercise pilot is `catalogue/candidate-manifest-v2.csv`; the eight-exercise pilot runtime subset is `catalogue/runtime/pilot-staging-v1.json`. Source storage is mixed: CSV authoring/reference data, JSON metadata/maps/contracts/releases, and Markdown governance/design documents. Both CSV manifests act as manifests. Release metadata exists in the canonical map and runtime envelope: contract version 1, Schema 2.0, catalogue `pilot-1.0`, source commit `47523c6`, channel, scope, count, and checksum.

`catalogue/catalogue-audit.md` reports an earlier v1 validation of 10 rows and is not a full repository audit.

## 4. Schema and taxonomy

### Versions and executable truth

- Schema v1: version selected by validator CLI as `"1"`; no per-row version column and no standalone schema file. Executable shape is `V1_REQUIRED_COLUMNS`, `V1_REQUIRED_VALUES`, controlled-field constants in `validate_catalogue.py`, plus `reference/controlled-values.json` and `reference/equipment.csv`.
- Schema v2: `2.0`, present on all 48 records and in `reference/v2/schema-version.json`. Executable shape is `V2_COLUMNS`, required/controlled/free-field constants and governance rules in `validate_catalogue.py`, plus the v2 reference directory.
- Runtime Contract: v1, constrained by `runtime-catalogue-contract-v1.schema.json`.

### Reference architecture

V2 reference CSVs use `value, slug, status, description, notes, sort_order`. Status is one of Active, Caution, Deprecated, Retired. The taxonomy source of truth for validation is the v2 CSV set, not Kotlin. It covers aliases/AI/governance, Android categories, equipment/attachments/facilities/loading/grips/positions, exercise families/roles/variation/classification, movement and joint actions, muscles, goals/rep styles, safety flags, and review/content-origin states.

The reference sets are expandable governed vocabularies because rows carry stable slugs and lifecycle status. By contrast, hard-coded enums include Schema version, reference lifecycle statuses, runtime channels, mapping identity sources, alias types, relationship types, and runtime distribution scope.

Relationship fields in Schema v2 are `parent_exercise_key`, `progression_keys`, `regression_keys`, and `supersedes_key`. Governance fields include review status/notes, content origin, coaching/clinical review status, AI assistance/review/suitability fields, retired keys, and human verification. Provenance is carried by `source_provenance`, `human_verified_fields`, content origin, release source commit, catalogue version, and checksum.

### Duplicate truth

Material duplication exists:

- Schema v2 columns and required-field rules are independently encoded in Python and described in Markdown/`SCHEMA-INVENTORY.csv`.
- Reference filenames and field-to-reference mappings are hard-coded in Python while the values live in CSV.
- Runtime aliases and relationships are enumerated independently in Python and Runtime JSON Schema; Kotlin models accept strings rather than sharing those enums.
- Runtime contract version, channel, field names, and checksum rules are independently represented in JSON Schema, Python, and Kotlin.
- Pilot membership is hard-coded as eight keys in Python and repeated in the mapping, fixture, tests, and documentation.
- Schema/runtime versions and release metadata are repeated across reference metadata, map, fixture, contract, code, tests, and docs.
- Kotlin/Room stores selected classifications as columns and whole subdocuments as JSON, producing deliberate representation duplication.

The Android production category list/seed model remains a separate older truth from Schema v2's `android-categories.csv` and runtime family/movement taxonomy.

## 5. Canonical identity

`canonical-id-map-v1.json` contains exactly eight one-to-one mappings:

| Catalogue key | Canonical ID | Identity source |
|---|---|---|
| `barbell_bench_press` | `bench_press` | legacy seed |
| `bulgarian_split_squat` | `ex_bulgarian_split_squat` | new allocation |
| `farmers_carry` | `ex_farmers_carry` | new allocation |
| `goblet_squat` | `ex_goblet_squat` | new allocation |
| `plank` | `plank` | legacy seed |
| `push_up` | `ex_push_up` | new allocation |
| `seated_cable_row` | `ex_seated_cable_row` | new allocation |
| `selectorized_chest_press` | `ex_selectorized_chest_press` | new allocation |

The transformer requires unique keys/IDs, exact membership of the hard-coded pilot set, valid identity sources, and slugs. The architecture document declares published IDs immutable and non-reusable, but the map has no explicit immutable flag or historical ledger enforcement. Aliases do not map source identities to alternate records; aliases are search data attached to the resolved canonical exercise. Forty of the 48 source exercises intentionally have no canonical mapping because this map is explicitly constrained to the eight-record pilot. No mapped pilot key is missing from the source.

## 6. Runtime contract compared with Schema v2

The envelope contains: runtime contract version, Schema version, catalogue version, source catalogue commit, release channel (`pilot_staging` or `production`), fixed distribution scope, record count, exercises, and SHA-256 checksum.

Each runtime exercise contains canonical ID, display name; classification (family, primary movement pattern, difficulty, laterality, compound/isolation); aliases and keywords; primary/secondary/stabiliser muscles; required equipment and attachments; six coaching/safety strings; and resolved relationships (`parent`, `progression`, `regression`, `supersedes`).

| Classification | Schema v2 fields |
|---|---|
| RUNTIME | canonical name (as display name), selected classification fields, aliases/keywords, muscle groups, equipment/attachments, setup/execution/errors/safety/range/breathing, four relationships |
| AUTHORING ONLY | variation type; secondary movement patterns; joint actions; support/torso/loading/grip/bench attributes; external load; goals; rep styles; loadability; substitution group; temporary Android category |
| GOVERNANCE ONLY | review status/notes, content origin, coaching/clinical review status, AI tasks/flags/suitability, provenance, human-verified fields |
| UNCLEAR | exercise role, technical complexity, facility tier, contraindication flags, manufacturer aliases; these could plausibly be runtime features but are currently projected out |

The runtime contract is a deliberately narrow projection. It consumes useful identity, discovery, anatomy, equipment, coaching, and relationship knowledge, while dropping much of the 58-column authoring model.

## 7. Transformer

`tools/catalogue/build_runtime_catalogue.py` implements:

1. Read the 48-row v2 CSV and all required references.
2. Run full Schema v2 validation; fail on any finding and fail unless the row count is exactly 48.
3. Index source rows and require all eight hard-coded pilot keys.
4. Load and validate the exact eight-entry identity map.
5. For `production`, reject records unless approved, non-pending-origin, review-complete/not-required, and free of AI review flags. `pilot_staging` permits current drafts.
6. Map reference display values to governed slugs.
7. Resolve relationships against all 48 source keys; mapped in-subset targets become canonical edges, while valid targets outside the selected pilot are deliberately omitted.
8. Project the runtime fields and validate minimal runtime invariants.
9. Sort aliases by type/normalised/value, keywords by normalised value, relationships by type/target, and exercises by canonical ID. JSON object keys are sorted.
10. Construct envelope metadata from constants and the map.
11. Compute SHA-256 over compact canonical JSON of the payload without `checksum`.
12. Serialize sorted, indented UTF-8 JSON with one final LF.

Failures raise `RuntimeBuildError`, print a single failure message to stderr, and return exit code 1. Normal mode writes the target; `--check` reads without writing and requires byte equality.

Safe checks:

- Python unit discovery: 51 tests run, 51 passed.
- `--check` against the tracked fixture: failed `Fixture is not reproducible`.
- A build to a temporary file produced the same record count, contract, channel, and checksum; `git diff --no-index` found no textual change. The mismatch is line endings: tracked CRLF bytes versus serializer LF bytes. Thus semantic/checksum content matches, but the project's byte reproducibility contract currently does not.

## 8. Pilot runtime release

- Catalogue version: `pilot-1.0`.
- Contract: 1; Schema: 2.0.
- Channel: `pilot_staging`.
- Scope: `official_catalogue_release`.
- Declared source commit: `47523c6` (not current HEAD).
- Count: 8.
- Checksum: `4855f1385f96b8b30a4116d3252639522d4a97746717cd20afefb0b757ebc005`.
- Exercises: Barbell Bench Press (`bench_press`), Bulgarian Split Squat (`ex_bulgarian_split_squat`), Farmer's Carry (`ex_farmers_carry`), Dumbbell Goblet Squat (`ex_goblet_squat`), Push-Up (`ex_push_up`), Seated Cable Row (`ex_seated_cable_row`), Selectorized Chest Press (`ex_selectorized_chest_press`), Plank (`plank`).
- Relationships: zero. Valid source relationships to records outside the eight-key map are omitted by design.
- Transformer parity: semantic content and checksum match; byte-for-byte check fails on line endings.

## 9. Android integration and stop point

Exact implementation:

- DTOs: `RuntimeCatalogueModels.kt`.
- Room entities: `CatalogueStagingEntities.kt` (release, exercise, alias, search token, relationship).
- DAO: `CatalogueStagingDao.kt`.
- Database registration/migration: `StrengthDatabase.kt`, Room v10, abstract staging DAO, entities, and `MIGRATION_9_10`.
- Parser/importer: `PilotCatalogueImporter.kt`.
- Asset source: `app/build.gradle.kts` maps main assets to `../catalogue/runtime`; importer loads `pilot-staging-v1.json`.
- Tests: `PilotCatalogueImporterTest.kt`.

Flow:

```text
catalogue/runtime/pilot-staging-v1.json
  → Android asset source set
  → Moshi RuntimeCatalogueEnvelope parser
  → contract/channel/checksum/count/identity/relationship verification
  → PilotCatalogueImporter transaction
  → catalogue_staging_release/exercise/alias/search_token/relationship
  → STOP
```

There is no production call site for `PilotCatalogueImporter`, no application startup installation, and no repository/service that exposes staging rows. `CatalogueStagingDao.searchExact` is used only by its test.

Therefore:

- Production exercise UI uses the catalogue: **No**.
- Production search uses it: **No**; staging exact search exists only in DAO/test.
- Workout logging uses it: **No**.
- Exercise selection uses it: **No**.
- Current state: **isolated staging only**.

The application continues to use `Exercise`, `StrengthDao`, `StrengthRepository`, and 21 legacy seed rows/categories in `StrengthDatabase.kt`.

## 10. Room database and migrations

Candidate-Data's Room database version is 10. Registered migrations are 1→2, 2→3, 3→4, 4→5, 5→6, 6→7, 7→8, 8→9, and 9→10.

`MIGRATION_9_10` adds five isolated staging tables:

- release metadata keyed by channel;
- exercises keyed by canonical ID;
- aliases keyed by canonical ID/normalised/type, with canonical-ID and normalised indexes;
- search tokens keyed by canonical ID/normalised/source, with indexes;
- relationships keyed by source/target/type, with source/target foreign keys and indexes.

Exercise-linked rows cascade on exercise deletion. The migration does not change, copy, or link the production `exercise` table. Its test proves pre-existing production and custom rows survive and a staging table exists. This historical migration must not be assumed applicable to current main, whose database evolution has diverged.

## 11. Transaction safety

Commit `373ef91` added `persistenceFailureRollsBackEntireReplacementTransaction`. It first imports pilot 1.0, installs a SQLite trigger that aborts alias insertion, changes the version to a signed `pilot-1.1-rollback-test`, and attempts replacement.

The importer validates before mutation, then executes deletion of old exercises/release and insertion of release, exercises, aliases, tokens, and relationships inside `database.withTransaction`.

The test proves that a persistence failure after deletion and after some new insert work rolls the entire replacement back: the prior release pointer remains `pilot-1.0`, all eight prior exercises remain, and a representative prior alias remains. Code places relationships inside the same transaction, so failed-release relationship writes cannot commit independently. It does not enumerate every table after failure or simulate failure at every statement, but the Room transaction boundary covers all catalogue mutations.

Consequently, according to code plus the forced-failure test, a failed import cannot leave partial release metadata, partial exercise rows, failed-release relationships, or a corrupted active channel pointer. Parse/checksum/identity/relationship failures occur before the transaction.

## 12. Test inventory and results

### Python

- `test_validate_catalogue.py`: 3 Schema v1 validation tests.
- `test_validate_catalogue_v2.py`: 32 Schema v2/reference/taxonomy/governance tests.
- `test_build_runtime_catalogue.py`: 16 transformer/contract/identity/governance/determinism tests.
- Total: 51, all passed in 1.948 seconds.

The 16 transformer tests include the eight-record projection, contract shape, exact identity map, missing/duplicate identity failures, version rejection, production governance rejection, deterministic bytes, checksum sensitivity, malformed data and relationships, taxonomy/search projection, and official/custom separation.

### Android catalogue test class

`PilotCatalogueImporterTest.kt` contains 11 Robolectric tests: parsing/eight IDs; pre-mutation version/channel/count rejection; tamper rejection; duplicate/unknown relationship rejection; persistence; idempotence; stable-ID update; rejected replacement retention; forced persistence rollback; staging search; official/custom isolation; and 9→10 migration preservation.

The class could not run. After locating Android Studio's Java runtime, Gradle failed before configuration because the checked-in 78,783-byte `gradle/wrapper/gradle-wrapper.jar` is not a valid ZIP/JAR (`zip END header not found`). Therefore Android unit, Room integration, migration, and rollback result count is **0 executed / blocked**, not a pass.

Other Android tests exist but are application sprint/UI tests, not catalogue tests. There is one generic instrumentation test and no catalogue instrumentation test.

## 13. Firestore / official catalogue

Repository search finds `catalogueReleases`, `officialExercises`, and official release language only in `docs/exercise-catalogue-integration.md`; no implementation references those collections.

Classification: **DESIGN ONLY**.

- `firestore.rules` permits authenticated owners under `/users/{userId}/**` and declares no official-catalogue read rules.
- `firestore.indexes.json` has no indexes or overrides.
- No publisher, downloader, manifest resolver, collection creation, or release activation implementation exists.
- Existing sync writes custom exercises to `/users/{humanUserId}/customExercises/{globalId}`.
- Official staging data is local/read-only and separate from the production/custom `exercise` table. Tests explicitly prove isolation.

No Firestore collections were created.

## 14. Documentation reality check

| Document | Status | Reality |
|---|---|---|
| `exercise-catalogue-integration.md` | PARTIALLY STALE | Accurate staging architecture/identity/transaction boundary, but Firestore distribution and wider integration are design; “runtime contract consumed locally” overstates production use because importer has no call site |
| `exercise-catalogue/README.md` | PARTIALLY STALE | Useful catalogue index, but should be checked against current 48-row/runtime state |
| `HUMAN-EXERCISE-SCHEMA-v2.md` | CURRENT as descriptive design | Matches broad v2 authoring model; not executable schema |
| `REFERENCE-DATA-v2.md` | CURRENT | Aligns with governed v2 reference directory |
| `SCHEMA-v1-to-v2-MIGRATION.md` | HISTORICAL | Describes completed transition context; v1 remains only as demo |
| `SCHEMA-ALIGNMENT.md` | PARTIALLY STALE | Alignment discussion predates/narrates subsequent refinements |
| `SCHEMA-INVENTORY.csv` | PARTIALLY STALE | Parallel documentation truth to Python/current header |
| taxonomy documents (`TAXONOMY`, muscles, equipment, movement patterns) | PARTIALLY STALE | Explanatory; executable truth is CSV and validator |
| principles, alias, duplicate, review protocol | CURRENT policy / PARTIALLY ENFORCED | Some rules are enforced by Python; not all policy is mechanically guaranteed |
| `PILOT-EXPORT-SPEC.md` | HISTORICAL | Runtime transformer/contract now supersede export planning |
| `RELEASE-ROADMAP.md` | HISTORICAL | Roadmap, not current implementation |
| `INTEGRATION-ORIGIN.md` | HISTORICAL | Provenance/context document |
| family Markdown files | HISTORICAL placeholders | Each is tiny; source records live in CSV, not per-family documents |
| `catalogue/catalogue-audit.md` | HISTORICAL | Only an old 10-row v1 validator result |

Main contradictions/overstatements:

- Documents describe Firestore namespaces and distribution, but code/rules/indexes do not implement them.
- “Consumed locally” is true only of an importer class/test, not of the running product.
- Immutable canonical identity is policy, not enforced by an append-only registry.
- Runtime release reproducibility is claimed/tested, yet tracked line endings make `--check` fail.
- The documentation presents schemas as architecture, while no v1/v2 JSON Schema exists.

## 15. Ontology, catalogue, runtime, and Room boundaries

### A. Human Ontology

Stable canonical exercise identity, aliases, muscles, movement patterns, joint actions, equipment concepts, exercise relationships, and stable slugs are shared controlled knowledge.

### B. Governed Catalogue

The 48 authored exercise records; coaching/safety text; taxonomy assignments; review states/notes; AI assistance and flags; clinical/coaching review; provenance; human verification; release eligibility; and retired keys.

### C. Runtime Contract

The versioned/checksummed official release envelope and the safe projection of canonical identity, selected classification, search terms, anatomy, equipment, coaching, and resolvable relationships.

### D. Human Strength Room

Offline storage of installed release metadata and projected staging exercises/search/relationships, alongside the independent legacy `exercise` table used by product features.

Blurred boundaries:

- The canonical map is release/pilot-specific while canonical identity is ontology-level.
- Python owns pilot membership and governance rules as well as transformation.
- Reference data mixes ontology concepts, product categories, authoring governance, review workflows, and AI process fields in one directory.
- The runtime contract carries Schema v2/source commit governance provenance.
- Room duplicates JSON subdocuments and indexed classification columns.
- Legacy production seed identity overlaps two canonical IDs but remains a separate product store.
- Android categories sit in governed reference data although they are product-specific.
- Runtime relationship omission depends on pilot selection rather than an explicit partial-release relationship policy in the contract.

## 16. Technical debt

1. Corrupt Gradle wrapper JAR blocks all Android verification.
2. Runtime `--check` is line-ending-sensitive and currently fails on the tracked fixture.
3. No executable JSON Schema exists for authoring Schema v1/v2.
4. Critical truth is duplicated across CSV, Python, JSON Schema, Kotlin, tests, and docs.
5. Canonical immutability is documented but not backed by an append-only identity registry/history check.
6. Pilot membership is hard-coded and repeated.
7. The runtime fixture declares old source commit `47523c6`.
8. Staging import is unreachable from production code.
9. No official Firestore distribution implementation, rules, indexes, or client.
10. Runtime DTO strings do not enforce several JSON Schema enumerations at parse time.
11. Relationship omission for out-of-subset records is silent and policy-laden.
12. V2 validation requires exactly 48 rows in the transformer, coupling tooling to this batch.
13. Family Markdown placeholders and historical audit artifacts can be mistaken for current sources.

## 17. Risks

- Promoting `MIGRATION_9_10` onto current main without reconciling database history can conflict with later main migrations/version.
- Treating staging as integrated would produce a false product-readiness assessment.
- A release may be semantically correct yet fail CI/check on Windows line endings.
- Repairing line endings by regeneration could create a large/unintended tracked change and was explicitly out of scope.
- Diverged branches mean Android/Firebase assumptions from Candidate may no longer apply to current main.
- Mutable edits to the map could silently reassign identity unless independent governance is added.
- Official catalogue Firestore paths have no security/distribution implementation; deploying documented paths prematurely would be unsafe.
- Dropped authoring fields may later be assumed available to Android when they are not.
- The forced rollback test is strong but could not be rerun in this environment due the broken wrapper.

## 18. Exact next recommended task

Perform a **read-only Candidate-to-current-main integration-delta assessment** focused on Room schema/migration numbering, Gradle wrapper integrity, existing exercise consumers, and Firebase rules. Produce a promotion plan that identifies the correct migration number and integration seams on current main.

Do not yet merge Candidate-Data, copy `MIGRATION_9_10`, repair/regenerate the runtime fixture, wire the importer into production, or create Firestore collections. The first implementation sprint should be chosen only after that delta assessment resolves the branch-divergence and test-run blockers.

## Audit commands and final integrity

Safe actions performed: Git status/branch/remotes/fetch/log/ref comparisons; repository searches and file reads; Python unit discovery; transformer `--check`; temporary out-of-tree transformer build and textual comparison; targeted Gradle test attempt; final Git status/diff inspection.

No implementation or generated repository file was modified by the audit.
