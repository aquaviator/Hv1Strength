# Governed Exercise Catalogue Contract v1

Firestore topology:

- `exercise_catalogue/current` — pointer/manifest for the currently published immutable release.
- `exercise_catalogue_releases/{releaseId}` — immutable release metadata.
- `exercise_catalogue_releases/{releaseId}/exercises/{exerciseId}` — immutable, app-neutral exercise documents.

Only `status=published`, `channel=production`, schema-compatible releases are consumable. Android clients are readers only. Draft editorial intake under `staging_exercises` is never consumed by Strength.

The canonical checksum is lowercase SHA-256 over UTF-8 JSON with lexicographically sorted object keys, no insignificant whitespace, and exercise documents sorted by permanent `exerciseId`.

Published releases are immutable. Deprecation hides an exercise from discovery but does not delete the retained Room identity needed by workout, history, routine, planner, favourite, or active-session references.

## Governed release workflow

Publication and activation are separate privileged operations and require separate
production authorizations. `tools/governed_catalogue.py` defaults to a write-free
generation operation and accepts only fixed catalogue paths.

1. `generate` validates the source and emits deterministic canonical metadata,
   documents, counts, and checksums without contacting Firestore.
2. `publish` creates only the immutable release metadata and exercise documents.
   It first creates an unreadable `publishing` manifest, writes exercises in
   guarded batches of at most 400, verifies the complete payload, and only then
   finalizes the manifest as `published`. It never writes the current pointer.
3. `verify` reads and validates one explicit immutable release and produces a
   compact receipt. It performs no writes.
4. `activate` reverifies the explicit release and writes only
   `exercise_catalogue/current`. It reports the previous and new release.

`rollback` uses the same verified pointer-only mechanism as activation, targeting
a previously verified immutable release. Rollback never deletes or rewrites a
release. A partial publication remains non-current and is failed closed on retry.

Production commands require the exact `hv1-platform` project, explicit release
ID/version/count/checksum, `--confirm-production`, and an authorized short-lived
token in `FIREBASE_ADMIN_ACCESS_TOKEN`. Credentials are never stored or printed.
