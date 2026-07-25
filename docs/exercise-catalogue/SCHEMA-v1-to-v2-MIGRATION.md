# Schema v1 to v2 Migration

## Boundary

`catalogue/candidate-manifest.csv` remains the frozen v1 migration source. `catalogue/candidate-manifest-v2.csv` is the separate v2 authority. A manifest must contain only one schema generation; mixed-schema rows are prohibited.

Migration changes catalogue metadata only. Existing Android and production IDs remain untouched. `temporary_android_category` is compatibility metadata and never becomes the long-term taxonomy.

## Preserved fields

- `catalogue_key`
- `canonical_name`
- `variation_type`
- `exercise_family`
- equipment values that remain valid
- difficulty technical complexity and facility tier
- business and review notes where still applicable
- aliases subject to global revalidation
- substitution group as a planning-only legacy relationship

## Renamed or reshaped fields

| v1 | v2 | Action |
|---|---|---|
| `parent_exercise` | `parent_exercise_key` | Resolve the conceptual name to a v2 key through human review |
| `secondary_movement_pattern` | `secondary_movement_patterns` | Convert to controlled multi-value form |
| `primary_muscle_group` | `primary_muscles` | Convert to anatomical controlled values |
| `secondary_muscle_groups` | `secondary_muscles` | Convert to anatomical controlled values |

## Human enrichment required

Migration must add and review:

- schema version and content origin
- laterality and compound/isolation classification
- primary and secondary joint actions
- support torso loading grip and bench metadata
- stabiliser muscles
- external-load and loadability classifications
- training goals and exercise roles
- coaching and clinical governance
- directed progression and regression references
- AI governance and provenance where applicable

Do not fill unknown values with `Not Applicable`. That value means genuinely inapplicable.

## Broad muscle conversion

Generally safe starting mappings:

| v1 | v2 starting value |
|---|---|
| Chest | Pectoralis Major |
| Biceps | Biceps Brachii |
| Triceps | Triceps Brachii |
| Forearms and Grip | Forearm Flexors and Forearm Extensors |
| Quadriceps | Quadriceps |
| Hamstrings | Hamstrings |
| Glutes | Gluteus Maximus |
| Adductors | Hip Adductors |
| Calves | Gastrocnemius and Soleus |
| Spinal Erectors | Spinal Erectors |
| Hip Flexors | Hip Flexors |

Human decisions are mandatory for `Back`, `Shoulders`, `Core`, `Full Body`, and `Abductors`. Their correct v2 values depend on the individual exercise.

## Movement migration

- Preserve genuine programming patterns such as Horizontal Push, Squat, and Hinge.
- Replace ambiguous legacy Flexion, Extension, and Lateral Flexion labels with precise v2 patterns where programming identity requires them, including Elbow Flexion, Elbow Extension, Trunk Flexion, Anti-Extension, and Anti-Lateral Flexion.
- Continue to express biomechanical actions independently through joint actions such as Trunk Flexion, Knee Extension, or Trunk Lateral Flexion.
- Lateral raises use Shoulder Abduction.
- Chest fly mechanics use Horizontal Adduction.
- Rear-delt fly mechanics use Horizontal Abduction.
- Carries isometrics mobility and machine records require individual review rather than mechanical defaults.

## Identity and relationship migration

- Preserve valid keys and never reuse retired keys.
- Resolve parent names to keys.
- Add supersedes keys only for reviewed replacements.
- Validate parent progression and regression graphs for missing references self-reference and cycles.
- Preserve existing production IDs entirely outside this migration.

## Alias revalidation

Re-run every alias against all v2 canonical names keys aliases abbreviations and legacy names. Manufacturer names remain aliases. Collisions require a recorded ownership decision; they must not be resolved by silent deletion.

## Review reset

Migrated demonstration rows return to Draft. Any future non-demonstration migration should return to Draft unless a named review decision explicitly carries forward evidence for every materially changed field. Architecture review precedes coaching and clinical review.

## Migration sequence

1. Freeze and checksum the v1 source.
2. Create v2 rows in a separate working manifest.
3. Apply mechanical field preservation and renames.
4. Resolve keys and relationships.
5. Enrich movement joint-action anatomy and setup fields.
6. Revalidate aliases and controlled values.
7. Reset review state and perform architecture review.
8. Run v1 and v2 validation independently.
9. Keep production export and ID allocation as separate authorised work.
