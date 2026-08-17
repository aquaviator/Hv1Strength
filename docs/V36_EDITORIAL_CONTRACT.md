# V36 governed exercise intelligence and editorial contract

V36 keeps every schema-1 field valid and adds optional exercise-intelligence fields. Existing canonical IDs are immutable. Additive fields cover secondary categories, modalities, stabilizers, joint actions, movement plane, kinetic chain, contraction emphasis, range-of-motion notes, force vector, biomechanical rationale, optional and substitutable equipment, environment suitability, skill level, compound classification, purpose, programming guidance, common use cases, contraindications, cautions, stop conditions, clinical supervision, and attributed evidence claims.

Evidence entries require `claim`, `citation`, an HTTPS `sourceUrl`, `reviewedAt`, and `reviewer`. Missing evidence remains visibly “not yet reviewed”; it is never converted into an authoritative claim.

## Editorial separation

Untrusted staging input follows `INGESTED → CLASSIFIED → REVIEWED → APPROVED → PUBLISHED`, with `REJECTED` available only after review. The deterministic classifier produces recommendations and reasons; it cannot approve, publish, or assign a new canonical ID. An editor explicitly chooses `NEW_EXERCISE`, `ALIAS`, `ENRICHMENT`, or `REJECT` and advances the lifecycle. Only `APPROVED` records can enter a release draft.

`tools/editorial_catalogue.py` is local-only and has no Firebase dependency. Generation is stable for identical base data, candidates, and decisions. The generated receipt names approved candidate IDs without including staging content. Production publication and activation remain separate, guarded operations outside this workflow.

Launch the round-trip review UI with `python tools/editorial_workbench.py --candidates tests/fixtures/editorial_candidates.json --catalogue app/src/main/assets/strength-exercise-catalogue.json --decisions build/editorial-decisions.json`. It binds only to `127.0.0.1`; recorded decisions remain local and still require a separate lifecycle approval before draft generation.

## Cross-application contract

Shared fields are application-neutral: canonical identity, names, categories, anatomy, biomechanics, equipment, environment, skill, safety, evidence, and the union of measurement capabilities. Strength may render only supported capabilities. HIIT may later adopt the same governed records and render its supported subset; V36 does not modify or deploy HIIT.
