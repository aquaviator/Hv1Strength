# Muscle Taxonomy

Muscle metadata supports discovery and broad programming context. It is not a clinical claim, an EMG ranking, or a complete anatomical model.

## Rules

- `primary_muscle_group` is required and contains one controlled value.
- `secondary_muscle_groups` is required for the candidate architecture and contains one or more controlled values separated by `|`.
- Select the group most responsible for the exercise's intended training action as primary.
- Secondary groups should be materially involved, not an exhaustive list of every stabiliser.
- Use consistent group-level language rather than mixing muscles, regions, and movement patterns.
- Rehabilitation-specific interpretation must receive appropriate clinical review.

Controlled values are maintained in `catalogue/reference/controlled-values.json`. Proposed additions require anatomy review and an explicit migration decision for existing candidate rows.
