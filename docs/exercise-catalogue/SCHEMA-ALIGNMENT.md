# Repository Schema Alignment

This is an evidence-based comparison, not a production-schema proposal. Paths below are repository-relative and every technical claim cites a file and symbol.

## 1. Repository architecture discovered

The application is a single Android `app` module using Kotlin, Jetpack Compose, Room, and Firebase Firestore (`app/build.gradle.kts`). Room is the offline source through `StrengthDatabase`, `StrengthDao`, and `StrengthRepository`. Syncable entities implement `VersionedEntity`; queued commands are processed by `SyncEngineImpl`.

No pre-existing `docs/`, `catalogue/`, or `tools/` directories were found. Catalogue material can therefore use the proposed top-level paths without overwriting existing content.

Relevant production surfaces:

- Room entities: `app/src/main/java/com/example/data/Entities.kt` (`Exercise`, `WorkoutTemplateExercise`, `LoggedSet`)
- DAO: `app/src/main/java/com/example/data/StrengthDao.kt` (`StrengthDao`)
- database and bundled seed: `app/src/main/java/com/example/data/StrengthDatabase.kt` (`StrengthDatabase`, `StrengthDatabaseCallback.populateDatabase`)
- repository and command enqueueing: `app/src/main/java/com/example/data/StrengthRepository.kt` (`StrengthRepository.insertExercise`, `deleteExercise`)
- sync: `app/src/main/java/com/example/core/sync/SyncEngineImpl.kt` (`processCommand`, `downloadRemoteChanges`)
- shared versioning: `app/src/main/java/com/example/core/versioning/VersionedEntity.kt` (`VersionedEntity`)
- IDs: `app/src/main/java/com/example/core/identity/GlobalIdGenerator.kt` (`GlobalIdGenerator.generate`)
- custom-exercise UUID: `app/src/main/java/com/example/ui/viewmodel/RoutineViewModel.kt` (`createCustomExercise`)
- category filters: `app/src/main/java/com/example/ui/screens/ExerciseScreen.kt` (`ExerciseScreen`)
- display strings: `app/src/main/res/values/strings.xml`

## 2. Production exercise model summary

`Exercise` is a Room entity for table `exercise` (`Entities.kt:108-126`). Its catalogue-bearing fields are:

- `id: String`, required primary key, no default;
- `name: String`, required, no default;
- `category: String`, required, no default;
- `isCustom: Boolean`, required, default `false`.

It also inherits required sync/version properties from `VersionedEntity`: `globalId`, `humanUserId`, timestamps, nullable deletion and conflict markers, `revision`, `syncStatus`, and `originDeviceId` (`VersionedEntity.kt:6-16`; defaults in `Entities.kt:116-125`).

There are no production fields for equipment, muscles, movement patterns, difficulty, complexity, aliases, parent exercises, variations, substitution relationships, coaching metadata, launch metadata, or facility tier.

## 3. Room representation

`StrengthDatabase` registers `Exercise` and is version 9 with `exportSchema = false` (`StrengthDatabase.kt:14-31`). The exercise table uses Kotlin property names as Room columns because `Exercise` has no `@ColumnInfo` overrides.

`StrengthDao` reads non-deleted exercises ordered by `name`, resolves them by `id` or `globalId`, inserts with `OnConflictStrategy.REPLACE`, supports soft/hard deletion, and selects pending sync records (`StrengthDao.kt:72-89`, `265-269`, `314-316`, `366-368`).

The database callback seeds 21 global exercises with readable string IDs such as `bench_press`, `deadlift`, and `squat`; those values are also assigned to `globalId` (`StrengthDatabase.kt:713-758`). This is bundled Kotlin seed data, not CSV/JSON import tooling. Migrations 1–9 exist in `StrengthDatabase.kt`; none add catalogue taxonomy to `exercise`.

## 4. Firestore representation

`SyncEngineImpl.processCommand` maps an `Exercise` to Firestore fields `globalId`, `id`, `name`, `category`, `isCustom`, `humanUserId`, `createdAt`, `updatedAt`, `revision`, `deletedAt`, `originDeviceId`, and `lastSyncedAt` (`SyncEngineImpl.kt:336-357`).

Documents are written to `users/{humanUserId}/customExercises/{globalId}`, not to a global production exercise collection (`SyncEngineImpl.kt:352-353`). Download enumerates `customExercises` and reconstructs `Exercise` from the same content fields (`SyncEngineImpl.kt:545-555`, `769-785`, `1016-1028`). `firestore.rules` authorises authenticated owners only beneath `users/{userId}`.

Consequently, the present Firestore flow is a user-owned custom-exercise sync model. The bundled global exercises remain Room seed data. The catalogue must not be treated as a Firestore document source.

## 5. Import and synchronisation flow

There is no catalogue CSV/JSON importer. `StrengthDatabaseCallback.populateDatabase` inserts the bundled exercise list on initial database creation (`StrengthDatabase.kt:716-758`).

`StrengthRepository.insertExercise` stores global exercises as `SYNCED`, while new custom exercises receive a generated `exercise_*` global ID, `PENDING_UPLOAD`, and an `ExerciseCreated`/`ExerciseUpdated` command (`StrengthRepository.kt:223-255`). The command queue is processed by `SyncEngineImpl`, which uploads to the owner’s `customExercises` subcollection. Remote changes are compared using `globalId`, `revision`, timestamps, deletion state, and device origin (`SyncEngineImpl.kt:545-590`).

This flow is intentionally unchanged.

## 6. Identity and naming strategy

- Seed `Exercise.id` and `globalId` are identical readable keys (`StrengthDatabase.kt:735-756`).
- Custom `Exercise.id` is `custom_${UUID.randomUUID()}` (`RoutineViewModel.kt:204-214`).
- Custom `globalId` is independently generated as `exercise_` plus 12 lowercase UUID-derived characters (`GlobalIdGenerator.kt:5-9`; `StrengthRepository.kt:226-234`).
- Firestore document ID is `globalId` (`SyncEngineImpl.kt:352-353`).
- `name` is the only canonical/display name. There is no slug, alias, translation, manufacturer-term, legacy-name, or structured-name field in `Exercise`.
- `revision` is entity sync versioning, not catalogue-definition versioning (`VersionedEntity.kt:6-16`).
- Only one base string resource file exists and exercise names are seeded as Kotlin literals (`strings.xml`; `StrengthDatabase.kt:735-756`).

Recommendation: keep `catalogue_key` **planning-only** during Pilot 1.0. It may later become an import/stable content key only after a product decision reconciles it with existing readable seed `id` values. It must not silently become a UUID, Firestore document ID, slug, or `globalId`.

## 7. Taxonomy comparison

| Concept | Application evidence | Catalogue value | Status | Recommendation |
|---|---|---|---|---|
| Exercise category | Free `String`; UI values `Chest`, `Back`, `Legs`, `Shoulders`, `Arms`, `Abs` (`Entities.kt:112`; `ExerciseScreen.kt:96,278`) | 13 exercise families | Controlled-Value Conflict | Map catalogue family to application category during a future import design; retain application values now |
| Equipment | No exercise field | Controlled equipment list | Missing From Production Schema | Keep planning-only |
| Primary/secondary muscles | No exercise fields; category is used as a muscle proxy (`WorkoutScreen.kt:681-682`) | Separate controlled muscle fields | Catalogue Manifest More Detailed | Keep planning-only; do not overload `category` |
| Movement patterns | No exercise field | Controlled movement vocabulary | Missing From Production Schema | Keep planning-only |
| Difficulty | No exercise field; user profile has training experience only (`Entities.kt:22`) | Exercise difficulty | Naming Conflict | Split concepts; keep catalogue difficulty planning-only |
| Technical complexity | No field | Controlled values | Missing From Production Schema | Keep planning-only |
| Facility/environment | No field | Controlled facility tier | Missing From Production Schema | Keep planning-only |
| Launch priority | No field | Controlled values | Planning Only | Keep planning-only |
| Module tags | No field | Controlled values | Planning Only | Keep planning-only |
| Review state | No field | Editorial workflow | Planning Only | Keep outside production |
| Aliases | Search compares only `name` in UI (`ExerciseScreen.kt:116`) | Three alias classes | Missing From Production Schema | Store in catalogue only; future search design requires product decision |
| Substitution group | No exercise relationship | Planning group | Missing From Production Schema | Keep planning-only pending relationship design |

The catalogue is more detailed across every content-taxonomy dimension. The application’s category values are the sole current controlled-looking vocabulary, but they are enforced only by UI lists rather than an enum or database constraint.

## 8. Manifest-to-schema inventory

The detailed field-level inventory is in `SCHEMA-INVENTORY.csv`. Summary:

| Manifest field | Production correspondence | Alignment |
|---|---|---|
| `catalogue_key` | Seed `Exercise.id` resembles a content key but custom IDs use UUIDs | Requires Human Decision |
| `canonical_name` | `Exercise.name` | Direct Match |
| `exercise_family` | `Exercise.category` at a broader/different granularity | Compatible With Transformation |
| all remaining taxonomy fields | No production field | Catalogue Manifest More Detailed / Missing From Production Schema |
| aliases | Search metadata absent | Missing From Production Schema |
| `substitution_group` | No substitution representation | Missing From Production Schema |
| editorial/business fields | No runtime purpose | Planning Only |

## 9. Planning-only fields

Keep these in the catalogue workspace unless separately authorised: `catalogue_key`, `parent_exercise`, `variation_type`, equipment, movement fields, muscle fields, difficulty, technical complexity, facility tier, commercial importance, launch priority, all alias classes, module tags, substitution group, review status, and review notes.

## 10. Conflicts

1. Catalogue families do not map one-to-one to the six application categories.
2. Application `category` is simultaneously used for navigation and as a muscle proxy.
3. Catalogue difficulty is exercise difficulty; existing “Beginner/Intermediate/Advanced” describes user training experience.
4. Seed IDs look like stable content keys but the repository has no explicit slug/content-key contract.
5. The original architecture assumed richer production taxonomy and structured exercise documents; the inspected `Exercise` model does not contain them.

## 11. Substitutions, localisation, aliases, and telemetry

No exercise substitution, progression, regression, equipment-exclusion, or movement-equivalence model was found. `supersetGroupId` groups exercises inside a workout template and must not be repurposed (`Entities.kt:151-174`). The UI’s “progression” value describes set-loading style stored inside template notes, not exercise ancestry (`WorkoutScreen.kt:558-584`, `3075-3083`).

There is no translated exercise-name architecture. Regional terms, slang, abbreviations, manufacturer aliases, and misspellings should remain catalogue alias metadata until a search/localisation design is approved. The only resource catalogue is `app/src/main/res/values/strings.xml`, while exercise names are Kotlin seed literals.

No wearable exercise telemetry schema was found. Logged performance uses `LoggedSet` fields such as reps, weight, RPE, duration, and distance (`Entities.kt:229-263`); `ExerciseIntelligence` derives performance summaries from logged sets. These are user-performance data, not catalogue metadata.

## 12. Unresolved decisions

- Approve an explicit family-to-category mapping, including calves, core, Olympic lifting, strongman, conditioning, and mobility.
- Decide whether current `Exercise.id` is a stable content key, legacy seed identifier, or future import target.
- Decide whether catalogue aliases ever enter production search and localisation.
- Decide whether substitution needs explicit directed typed relationships; simple groups are insufficient for asymmetric progression/regression.
- Define ownership of coaching, anatomy, and clinical review.
- Decide whether the long-term production schema should evolve; this review does not recommend or implement that change.

## Pilot 1.0 Compatibility Projection

The authoritative candidate manifest is intentionally richer than the current Android `Exercise` model. For Pilot 1.0, the approved temporary family-to-category compatibility mapping is:

| Catalogue family | Android compatibility category |
|---|---|
| Chest and Horizontal Pressing | Chest |
| Back and Pulling | Back |
| Squat and Knee-Dominant Legs | Legs |
| Hinge and Posterior Chain | Legs |
| Glutes, Adductors and Abductors | Legs |
| Calves | Legs |
| Shoulders | Shoulders |
| Arms and Grip | Arms |
| Core and Carries | Abs |

This compatibility layer is not the long-term Human Platform taxonomy. Olympic Weightlifting, Strongman, Conditioning, and Strength-Relevant Mobility are outside Pilot 1.0 unless an individual candidate maps cleanly and unambiguously to an existing Android category.

The approved production projection is:

| Catalogue or export concept | Current production projection |
|---|---|
| Manifest `canonical_name` | `Exercise.name` |
| Approved compatibility category | `Exercise.category` |
| Production ID | Separately allocated existing-style identifier |
| `isCustom` for seeded catalogue exercises | `false` |
| Every other manifest field | Planning and governance only |

All existing production exercise IDs are immutable legacy identifiers. `catalogue_key` remains planning-only: it is not a Room ID, UUID, Firestore document ID, `globalId`, or automatically allocated production identifier.

Aliases remain catalogue-planning metadata and are not application search features. Substitution groups and relationships likewise remain planning-only; they do not alter `supersetGroupId`, workout progression settings, Room, Firestore, Kotlin models, or runtime behaviour.

The future export boundary is specified in `PILOT-EXPORT-SPEC.md`. No production export is created by this integration.

## 13. Recommendations before catalogue generation

1. Keep all new metadata in the version-controlled catalogue workspace for Pilot 1.0.
2. Apply the approved temporary compatibility mapping during pilot review without changing runtime code.
3. Treat `catalogue_key` as planning-only and preserve all existing IDs.
4. Use planning-only substitution groups in the pilot, with explicit notes for directionality.
5. Validate names against the 21 bundled seeds before approval.
6. Run a 25–40 exercise pilot across five representative families only after the decisions above have named owners.

With these approved decisions recorded, the architecture is ready for Catalogue Pilot 1.0 content work. It remains intentionally separate from production import and runtime implementation.
