# HumanV1 canonical contract consumer

Android consumes the same `contracts/canonical-contract.json` versions and values as Workout Studio. `CanonicalFoundation.kt` validates discipline-appropriate workout prescriptions and plan dependencies, supplies deterministic occurrence identity, presents the five exact customer status labels, and implements only a guarded in-memory/emulator normalization projection.

Production migration is disabled. No global reset exists. Owner/revision conflicts and incomplete parent-only workouts fail closed; completed, skipped, and detached occurrences are preserved. Governed releases remain immutable and user-authored meaning is never overwritten automatically.
