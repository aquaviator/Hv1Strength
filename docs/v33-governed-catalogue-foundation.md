# V33 governed exercise catalogue foundation

V33 introduces an app-neutral, immutable-release catalogue contract while retaining the verified 264-exercise V32 package as the offline baseline. No production catalogue, rule, index, function, Play, or HIIT change is part of this candidate.

## Candidate topology

- `exercise_catalogue/current`: published pointer/manifest.
- `exercise_catalogue_releases/{releaseId}`: immutable release manifest.
- `exercise_catalogue_releases/{releaseId}/exercises/{exerciseId}`: governed exercise documents.
- `staging_exercises/{candidateId}`: administrative intake only; denied to app clients and never consumed by Strength.

Published production content is public read-only global data. Draft/staging content and every client write are denied. The existing ACTIVE schema-1 user ownership contract is unchanged.

## Android acceptance flow

Strength loads and verifies the bundled catalogue first. A background or user-requested check downloads the current release and validates publication state, channel, schema, minimum app version, count, stable IDs, graph references, and canonical SHA-256. Only a complete valid payload is applied, together with its accepted-release state, in one Room transaction. A failed or interrupted check retains the prior accepted library.

Stable IDs are upserted, so routines, sessions, favourites, and history remain linked. Custom exercise ID collisions are never overwritten. Governed exercises absent from a later release are retained in Room for historical references but are omitted from normal discovery. Deprecated documents likewise remain addressable while their `active` state removes them from normal discovery.

Room schema 13 adds only `catalogue_release_state`. Settings exposes version, count, source, last successful update, plain-language status, and a manual update action; checksum and typed validation details remain debug-only.

## Publication boundary

The local publisher defaults to dry-run, refuses non-`demo-` projects, requires an explicit localhost emulator endpoint for writes, validates the 264 original IDs, and classifies staging candidates without user data. Production publication requires a separate authorization naming the candidate rule and schema hashes.
