# V35 governed catalogue acceptance

V35 combines the V33 governed catalogue foundation with the accepted V32.1/V34 sign-in, membership, local-first, and unattended synchronization hotfix. Production Firebase is outside this acceptance scope.

## Local acceptance boundary

- Firebase project IDs must begin with `demo-`.
- The publisher requires an explicit loopback Firestore emulator endpoint and refuses any other host.
- Governed releases are immutable. Publication uses an incomplete/finalized,
  multi-batch-safe protocol and never activates the release implicitly.
- Android clients can read only published production-channel catalogue documents. Draft, staging, create, update, and delete operations are denied.
- Catalogue records remain separate from Human user data and never enter the user command queue.

## Exercised journeys

The acceptance suite covers the bundled 264-exercise baseline, equivalent and repeated releases, a 265th exercise, stable-ID editorial changes, deprecation retention, custom exercises, favourites, routines, sessions, logged sets, planner records, pending user commands, and every specified invalid or unavailable release classification. A connected Android test reads the real local Firestore release and applies it to an isolated Room database.

The second-client contract check treats recommendation flags only as discovery metadata and verifies that all governed records are app-neutral, readable without ownership fields, and include cardio, timed, and load-capable exercises.

Authentication, membership, offline entitlement, local-first persistence, reconnection, and different-account protection remain covered by the integrated hotfix regression suite. Catalogue failure is non-blocking: the last accepted or bundled snapshot remains available while later checks resume automatically.

## Production follow-up

Before any production catalogue publication or rules deployment, authorize the exact source commit and final hashes separately. Immutable publication and current-pointer activation also require separate authorizations. Production staging inspection, current-manifest changes, rules deployment, Functions deployment, and Play upload were not part of V35 acceptance.
