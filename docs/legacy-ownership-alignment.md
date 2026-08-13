# Legacy ownership alignment contract

Strength may upgrade legacy local ownership only when the app-private stored Firebase UID exactly matches the currently authenticated Firebase UID. Email, names, unsigned backups, missing identity evidence, different accounts, and ambiguous profiles never authorize migration.

The durable Room ownership graph moves in one SQLite transaction. Preferences change only after commit, and reminder work is reconstructed from committed planner occurrences. Malformed or unknown ownership-sensitive records block migration.

This contract is intended for later cross-app alignment. HIIT requires its own ownership inventory, migration implementation, tests, and physical acceptance after Strength passes its Play-installed legacy-phone test; this document makes no change to HIIT.
