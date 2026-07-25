# Review Protocol

Generated or imported candidates are proposals, never authoritative records.

## States

| State | Meaning |
|---|---|
| Draft | Unapproved proposal; may be incomplete or may have undergone automated or AI-assisted internal review without independent human verification |
| Architecture Reviewed | Identity, taxonomy, equipment, aliases, and duplication reviewed |
| Coaching Reviewed | Mechanics, programming role, difficulty, and substitutions reviewed by a qualified reviewer |
| Clinical Review Required | Candidate has rehabilitation or clinical implications requiring qualified clinical review |
| Approved | Required reviews complete and candidate accepted for a later production-mapping process |
| Rejected | Candidate does not meet admission or quality rules |
| Merged as Duplicate | Candidate identity retained in history but consolidated into another candidate |

## Review sequence

1. Author records the admission justification in `review_notes`.
2. Automated validation passes with warnings resolved or explicitly noted.
3. Architecture review checks hierarchy, canonical identity, aliases, controlled values, and duplicates.
4. Coaching review checks movement, muscle metadata, difficulty, complexity, and substitution role.
5. Clinical review occurs when claims, rehabilitation use, contraindications, or vulnerable populations are involved.
6. Approval records the reviewer and decision in version-control history.

No reviewer should approve their own generated batch without independent review. `Approved` does not itself create a Room record, Firestore document, UUID, slug, translation, coaching content, or production release.

## Review evidence and AI-assisted internal review

Automated checks and structured AI-assisted review passes may improve a Draft and are recorded through version-control history. They do not:

- change an AI-generated record into human-authored content;
- populate `human_verified_fields`;
- complete qualified coaching, anatomy, alias, or clinical review;
- clear the corresponding `ai_review_flags`; or
- make a record Approved.

`content_origin` preserves how the content originated rather than its current maturity. `AI Generated — Review Pending` therefore remains correct until the required independent human review is evidenced, even when internal AI-assisted review has occurred.

For Pilot 1.0, the reviewed commit and its diff provide sufficient internal traceability for batch-level AI-assisted review. Before production approval, the reviewer, review type, reviewed commit, decision, and date should be recorded in an append-only review record or release evidence rather than repeated across every manifest row.

`clinical_review_status = Not Required` means that no clinical claim or context triggers clinical review. It does not mean that a clinician reviewed or cleared the record.

## Approval definition of done

A record may move to `Approved` only when:

1. schema validation and regression tests pass;
2. canonical identity, aliases, movement taxonomy, equipment, and relationships have independent human review;
3. anatomy and joint actions have appropriate human domain review;
4. populated setup, execution, coaching, breathing, range, and safety content has qualified coaching review;
5. provenance and AI-assistance declarations are complete and preserved;
6. clinical or other specialist review is Completed when a record triggers it, or explicitly Not Required when it does not;
7. applicable `human_verified_fields` identify what was actually checked;
8. no unresolved `ai_review_flags` remain; and
9. version-control or review evidence records the reviewer, scope, reviewed commit, decision, and date.

Approval accepts catalogue content for later production mapping. It is not publication.

## Publication definition of done

Publication or application visibility is a separate release decision. It requires Approved content plus an authorised production mapping and release artifact covering stable runtime identifiers, supported media and licences, localisation status, application/database compatibility, search indexing, analytics identifiers, and release version. Missing publication dependencies do not prevent a structurally sound Draft from being used for integration planning when its non-production status remains explicit.

The approved content version should be the immutable reviewed commit or release artifact. Git history is sufficient during Pilot development; runtime publication should retain the approved source commit or content-version identifier so later edits can trigger re-review.

## Change control

Changes to controlled vocabularies require impact analysis and review. Canonical renames and merges require an alias, collision, and downstream-mapping assessment. Rejected and merged rows should remain traceable through version history.
