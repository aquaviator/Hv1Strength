# Movement Patterns

Movement patterns are programming descriptors rather than exhaustive biomechanical diagnoses.

## Assignment

- Every v2 candidate has exactly one `primary_movement_pattern`.
- `secondary_movement_patterns` is optional and should be used only when another pattern materially helps programming or substitution.
- Values come from `catalogue/reference/v2/movement-patterns.csv`.
- Avoid encoding plane, stance, tempo, or equipment in the pattern field.
- Carries, locomotion, loaded events, and conditioning use purpose-specific patterns rather than being forced into push or pull labels.
- Mobility candidates use `Mobility` when their principal purpose is accessible range or movement preparation.

## Refined identity patterns

`Elbow Flexion` and `Elbow Extension` distinguish arm-dominant isolation work from compound pulling and pressing. `Anti-Extension` and `Anti-Lateral Flexion` distinguish the defining direction of resisted trunk motion, while `Trunk Flexion` identifies dynamic trunk flexion. These values complement joint actions; they do not replace them.

Pattern changes require human taxonomy review because they affect search, substitutions, and reporting.

### AI governance

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `ai_assistance_tasks` | controlled multi | Conditional | `ai-assistance-tasks.csv` | Required for AI-assisted or AI-generated origin | Declares AI contribution | `Taxonomy Suggestion` | New |
| `ai_review_flags` | controlled multi | No | `ai-review-flags.csv` | Outstanding flags prevent final approval where relevant | Human review queue | `Anatomy Review Required` | New |
| `ai_suitability_tags` | controlled multi | No | `ai-suitability-tags.csv` | Tags do not grant autonomous authority | Permitted reviewed AI contexts | `Search Expansion` | New |
| `source_provenance` | text/reference | Conditional | — | Required for clinical contraindication or imported claims | Source or generation provenance | `Internal architecture review.` | New |
| `human_verified_fields` | field-name multi | No | This specification | Values must be known v2 field names | Fields explicitly checked by a human | `canonical_name|primary_joint_actions` | New |
