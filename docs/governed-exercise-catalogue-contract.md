# Governed Exercise Catalogue Contract v1

Firestore topology:

- `exercise_catalogue/current` — pointer/manifest for the currently published immutable release.
- `exercise_catalogue_releases/{releaseId}` — immutable release metadata.
- `exercise_catalogue_releases/{releaseId}/exercises/{exerciseId}` — immutable, app-neutral exercise documents.

Only `status=published`, `channel=production`, schema-compatible releases are consumable. Android clients are readers only. Draft editorial intake under `staging_exercises` is never consumed by Strength.

The canonical checksum is lowercase SHA-256 over UTF-8 JSON with lexicographically sorted object keys, no insignificant whitespace, and exercise documents sorted by permanent `exerciseId`.

Published releases are immutable. Deprecation hides an exercise from discovery but does not delete the retained Room identity needed by workout, history, routine, planner, favourite, or active-session references.
