# Review Protocol

Generated or imported candidates are proposals, never authoritative records.

## States

| State | Meaning |
|---|---|
| Draft | Unreviewed proposal; may be incomplete or generated |
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

## Change control

Changes to controlled vocabularies require impact analysis and review. Canonical renames and merges require an alias, collision, and downstream-mapping assessment. Rejected and merged rows should remain traceable through version history.
