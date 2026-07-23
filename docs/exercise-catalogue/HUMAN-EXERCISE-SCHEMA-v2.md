# Human Exercise Schema v2.0

Status: catalogue specification. This schema does not modify or describe an automatic Android, Room, Firestore, or production import.

## 1. Conventions

- The authoritative v2 manifest is `catalogue/candidate-manifest-v2.csv`.
- Every row declares `schema_version` as `2.0`.
- Controlled values use exact spelling and case from `catalogue/reference/v2/`.
- Multi-value cells use `|`; empty or repeated elements are invalid.
- Optional values use an empty cell unless a controlled field explicitly requires `Not Applicable`.
- Keys use lowercase ASCII snake_case and remain planning identifiers.
- Text fields contain explanatory prose; they must not encode structured controlled values.
- References use `catalogue_key`, never canonical names or production identifiers.
- AI-assisted and imported content remains non-authoritative until the required human reviews complete.

## 2. Field contract

Reference paths below are relative to `catalogue/reference/v2/`.

### Identity and governance

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `schema_version` | string | Yes | `schema-version.json` | Exactly `2.0` | Selects the explicit manifest generation | `2.0` | New; add to every migrated row |
| `catalogue_key` | string | Yes | — | Unique lowercase snake_case; never reused | Stable planning identity | `barbell_back_squat` | Preserve v1 key unless a reviewed demonstration key is replaced |
| `canonical_name` | string | Yes | — | Globally unique after normalisation | Manufacturer-neutral display identity | `Barbell Back Squat` | Preserve then recheck collisions |
| `parent_exercise_key` | key reference | No | v2 manifest | Must resolve; not self; parent graph acyclic | Connects a variation to its conceptual parent | `back_squat` | Convert v1 parent names through human mapping |
| `variation_type` | controlled string | Yes | `variation-types.csv` | Exact controlled value | Describes why the canonical record differs | `Equipment Variation` | Preserve where valid |
| `supersedes_key` | key reference | No | v2 manifest or `retired-keys.csv` | Must resolve; not self; cannot create reuse | Records an intentional replacement | `legacy_squat_name` | New |
| `review_status` | controlled string | Yes | `review-statuses.csv` | Approval blocked by outstanding coaching clinical or AI governance | Editorial maturity | `Draft` | Reset migrated records to Draft unless re-review is documented |
| `review_notes` | text | Yes | — | Non-empty; no personal data | Admission and review rationale | `Common foundational bilateral squat.` | Preserve and expand |
| `content_origin` | controlled string | Yes | `content-origins.csv` | AI origins require declared tasks and review | Provenance class | `Human Authored` | New; assign through migration review |

### Classification

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `exercise_family` | controlled string | Yes | `exercise-families.csv` | Exact controlled value | Stable catalogue workstream | `Squat and Knee-Dominant Legs` | Preserve |
| `temporary_android_category` | controlled string | No | `android-categories.csv` | Exact value when present | Compatibility metadata only | `Legs` | Derive through approved mapping; never treat as taxonomy |
| `laterality` | controlled string | Yes | `laterality.csv` | Name and mechanics should not contradict value | Side participation model | `Bilateral` | Human enrichment |
| `compound_or_isolation` | controlled string | Yes | `compound-isolation.csv` | Hybrid requires rationale; isolation normally has one primary joint action | Broad mechanical classification | `Compound` | Human enrichment |
| `exercise_role` | controlled multi | Yes | `exercise-roles.csv` | At least one unique value | Programming role rather than mechanics | `Primary Lift|Skill` | Human enrichment |
| `difficulty` | controlled string | Yes | `difficulty.csv` | Exact controlled value | Typical participant readiness | `Intermediate` | Preserve |
| `technical_complexity` | controlled string | Yes | `technical-complexity.csv` | Exact controlled value | Execution and coaching demand | `High` | Preserve |
| `facility_tier` | controlled string | Yes | `facility-tiers.csv` | Must be compatible with required equipment | Minimum typical facility context | `Strength Gym` | Preserve |

### Movement

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `primary_movement_pattern` | controlled string | Yes | `movement-patterns.csv` | Exactly one value | Primary programming pattern | `Squat` | Preserve only v2 patterns |
| `secondary_movement_patterns` | controlled multi | No | `movement-patterns.csv` | Unique; must not repeat primary | Material secondary patterns | `Carry` | Rename v1 secondary field and re-review |
| `primary_joint_actions` | controlled multi | Yes | `joint-actions.csv` | At least one; unique | Principal anatomical actions | `Hip Extension|Knee Extension` | Human enrichment |
| `secondary_joint_actions` | controlled multi | No | `joint-actions.csv` | Must not overlap primary | Material supporting actions | `Plantarflexion` | Human enrichment |
| `support_type` | controlled string | Yes | `support-types.csv` | Bench Supported requires Bench | Material body support | `Unsupported` | Human enrichment |
| `torso_position` | controlled string | Yes | `torso-positions.csv` | Dynamic requires rationale | Primary torso orientation | `Standing Upright` | Human enrichment |
| `loading_position` | controlled multi | Yes | `loading-positions.csv` | At least one; Variable requires rationale | Where resistance is applied or held | `Back Rack` | Human enrichment |
| `grip_type` | controlled multi | Conditional | `grip-types.csv` | Required for load-bearing gripped equipment | Material hand orientation | `Pronated` | Human enrichment |
| `bench_angle` | controlled string | Yes | `bench-angles.csv` | Non-NA requires Bench; use NA when no bench | Controlled bench orientation | `Flat` | Human enrichment |

### Anatomy

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `primary_muscles` | controlled multi | Yes | `muscles.csv` | At least one; no overlap with other muscle fields | Muscles most responsible for intended action | `Quadriceps|Gluteus Maximus` | Convert broad values with human review |
| `secondary_muscles` | controlled multi | No | `muscles.csv` | No overlap with primary or stabilisers | Material supporting muscles | `Hamstrings` | Convert broad values with human review |
| `stabiliser_muscles` | controlled multi | No | `muscles.csv` | No overlap with primary or secondary | Muscles materially acting as stabilisers | `Transverse Abdominis|Spinal Erectors` | New |

### Equipment

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `equipment` | controlled multi | Yes | `equipment.csv` | At least one; None cannot combine; landmine barbell use requires both | Generic equipment required | `Barbell|Bench` | Preserve and revalidate |
| `attachment_or_implement` | controlled multi | No | `attachments.csv` | Exact values; attachments remain separate from equipment | Cable or machine attachment detail | `Row Handle` | New |
| `external_load` | controlled string | Yes | `external-load.csv` | Must agree with bodyweight and equipment | Resistance source requirement | `External Load Required` | Human enrichment |

### Coaching

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `setup_cues` | text | No | — | AI-authored text requires coaching review | Concise setup guidance | `Set the bar across the upper back.` | New; do not fabricate |
| `execution_cues` | text | No | — | AI-authored text requires coaching review | Concise execution guidance | `Drive through the whole foot.` | New |
| `common_errors` | text | No | — | Claims require review | Common technical errors | `Losing foot pressure.` | New |
| `safety_notes` | text | No | — | Safety claims require provenance and review | Reviewed risk-management notes | `Use safeties when appropriate.` | New |
| `range_of_motion_notes` | text | No | — | Clinical claims require provenance | Reviewed range context | `Use the reviewed controllable range.` | New |
| `breathing_bracing_notes` | text | No | — | Requires coaching review when populated | Breathing and bracing context | `Brace before initiating the repetition.` | New |
| `coaching_review_status` | controlled string | Yes | `coaching-review-statuses.csv` | Coaching text or AI coaching draft cannot be complete without review | Coaching governance | `Required` | New |
| `clinical_review_status` | controlled string | Yes | `clinical-review-statuses.csv` | Required blocks Approved until Completed | Clinical governance | `Not Required` | New |

### Programming

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `training_goals` | controlled multi | Yes | `training-goals.csv` | At least one unique value | Common legitimate programming uses | `Maximal Strength|Hypertrophy` | Human enrichment |
| `suitable_rep_styles` | controlled multi | No | `suitable-rep-styles.csv` | Exact unique values | Broad prescription formats | `Low Repetition|Moderate Repetition` | New |
| `loadability` | controlled string | Yes | `loadability.csv` | Exact value | Practical progressive-loading capacity | `Very High` | Human enrichment |
| `substitution_group` | string | No | — | Lowercase snake_case when present | Planning cluster; not proof of equivalence | `squat_bilateral` | Preserve |
| `progression_keys` | key-reference multi | No | v2 manifest | Resolve; not self; graph acyclic | Directed progression relationships | `barbell_front_squat` | New |
| `regression_keys` | key-reference multi | No | v2 manifest | Resolve; not self; graph acyclic | Directed regression relationships | `goblet_squat` | New |
| `contraindication_flags` | controlled multi | No | `contraindication-flags.csv` | Claims require provenance and clinical review | Review prompts rather than diagnoses | `Range of Motion Review` | New |

### Search

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `search_aliases` | string multi | No | — | No canonical-name or key collision | Common discovery terms | `Back Squat` | Preserve and globally revalidate |
| `manufacturer_aliases` | string multi | No | — | Manufacturer terms never canonical | Brand or product terminology | `Example Brand Squat` | Preserve and revalidate |
| `regional_aliases` | string multi | No | — | Regional English only pending localisation design | Regional terminology | `Press-Up` | Preserve |
| `abbreviations` | string multi | No | — | No collision or keyword stuffing | Common abbreviations | `RDL` | New |
| `legacy_names` | string multi | No | — | No collision; requires history | Retired canonical names | `Legacy Squat Name` | New |
| `search_keywords` | string multi | No | — | Concise; no sentences or keyword stuffing | Supplemental discovery concepts | `bilateral squat` | New |

### AI governance

| Field | Type | Required | Reference | Validation | Purpose | Example | Migration |
|---|---|---:|---|---|---|---|---|
| `ai_assistance_tasks` | controlled multi | Conditional | `ai-assistance-tasks.csv` | Required for AI-assisted or AI-generated origin | Declares AI contribution | `Taxonomy Suggestion` | New |
| `ai_review_flags` | controlled multi | No | `ai-review-flags.csv` | Outstanding flags prevent final approval where relevant | Human review queue | `Anatomy Review Required` | New |
| `ai_suitability_tags` | controlled multi | No | `ai-suitability-tags.csv` | Tags do not grant autonomous authority | Permitted reviewed AI contexts | `Search Expansion` | New |
| `source_provenance` | text/reference | Conditional | — | Required for clinical contraindication or imported claims | Source or generation provenance | `Internal architecture review.` | New |
| `human_verified_fields` | field-name multi | No | This specification | Values must be known v2 field names | Fields explicitly checked by a human | `canonical_name|primary_joint_actions` | New |

## 3. Cross-field validation

- Lateral-raise identities must include `Shoulder Abduction` and must not substitute `Trunk Lateral Flexion`.
- Fly identities should include Horizontal Adduction; rear-delt fly identities should include Horizontal Abduction.
- Horizontal Push without Horizontal Adduction or Elbow Extension is suspicious.
- Vertical Push without Shoulder Flexion or Shoulder Abduction is suspicious.
- Squat should describe hip and knee flexion/extension across its primary and secondary actions.
- Hinge should describe Hip Flexion and Hip Extension.
- Isolation records with several unrelated primary joint actions are suspicious.
- Bench-supported records and non-NA bench angles require Bench equipment.
- A Landmine record using a barbell requires both Barbell and Landmine.
- Bodyweight loading must agree with `external_load`.
- Clinical review Required prevents Approved.
- AI-generated review-pending content prevents Approved.

Suspicious biomechanical combinations are warnings unless a rule protects structural integrity or prevents a known misclassification.

## 4. Production boundary

Schema v2.0 is versioned catalogue architecture. It allocates no production IDs, changes no runtime model, creates no Firestore document, and performs no Room migration. Any future export remains separately authorised.
