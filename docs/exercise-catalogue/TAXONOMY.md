# Catalogue Taxonomy

## Entity model

| Level | Purpose | Candidate field | Separate production record required? |
|---|---|---|---|
| Category | Broad navigation/reporting | Derived from family during later mapping | No |
| Exercise family | Stable content workstream | `exercise_family` | No |
| Parent exercise | Relates progressions, regressions, and variants | `parent_exercise` | No |
| Canonical exercise | Primary review candidate | `canonical_name` | Only after approval and production mapping |
| Meaningful variation | Records a justified mechanical or programming distinction | `variation_type` | Not automatically |
| Equipment variation | Identifies equipment-dependent execution | `equipment` and `variation_type` | Not automatically |
| Search alias | Improves discovery without duplication | alias fields | Never |

## Candidate identity

`catalogue_key` is a human-readable, lowercase planning key using ASCII letters, digits, and underscores. It is unique within the manifest and must not be treated as a UUID, production exercise ID, or slug without an approved mapping decision.

## Field semantics

- `exercise_family`: one value from the family reference list.
- `parent_exercise`: manufacturer-neutral conceptual parent name.
- `canonical_name`: unique display candidate in neutral English.
- `variation_type`: controlled planning description: `Base`, `Meaningful Variation`, `Equipment Variation`, `Progression`, or `Regression`.
- `primary_movement_pattern` and `secondary_movement_pattern`: controlled movement labels; secondary may be empty.
- Muscle fields: controlled labels, with `|` separating multiple secondary groups.
- `difficulty` describes typical participant readiness, while `technical_complexity` describes execution and coaching demand.
- Business fields guide sequencing, not exercise validity.
- `future_module_tags` use controlled module values separated by `|`.
- `substitution_group` is a planning relationship, not proof of interchangeability.

## Null and multi-value rules

Required values may not be blank. Optional values use an empty cell. Multi-value fields use `|`, contain no duplicates, and preserve reference-file spelling. Do not encode structured values in prose.

## Lifecycle

Rows progress through the states in `REVIEW-PROTOCOL.md`. A state describes review maturity, not production availability.
