# Reference Data v2

## Location and contract

Schema v2 controlled vocabularies live in `catalogue/reference/v2/`. CSV reference files use:

```text
value,slug,status,description,notes,sort_order
```

- `value` is the exact case-sensitive manifest value.
- `slug` is a unique lowercase snake_case reference identity within its file.
- `status` is one of `Active`, `Caution`, `Deprecated`, or `Retired`.
- `description` states the intended semantic use.
- `notes` records cautions migration constraints or review boundaries.
- `sort_order` is a unique positive integer used for stable presentation.

`schema-version.json` declares the schema version separator slug pattern column contract and reference lifecycle values.
`retired-keys.csv` is the permanent registry of catalogue keys that may never be reused. It intentionally begins empty.

## Adding a controlled value

1. Confirm an existing value cannot represent the concept accurately.
2. Check value and slug uniqueness.
3. Write a neutral description and any required caution.
4. Assess every existing manifest row and migration mapping affected.
5. Obtain architecture review.
6. Obtain coaching anatomy or clinical review when semantics require it.
7. Add validator fixtures for new conditional behaviour.
8. Run both schema generations and the complete catalogue test suite.

Manufacturer or product names must not become canonical equipment values. Attachments remain separate from equipment.

## Caution values

Values including Hybrid Dynamic Variable and Not Applicable require explicit notes. They must not become defaults for uncertainty. Review notes should explain their use on each record when the choice is not self-evident.

## Deprecation and retirement

- `Deprecated` values remain readable for migration but are invalid for new Approved records.
- `Retired` values remain reserved permanently.
- Never delete or reuse a deprecated or retired value or slug.
- Never reuse a key recorded in `retired-keys.csv`; a replacement may reference it through `supersedes_key`.
- Replacement values must be documented before migration.
- Removal from a future schema major version requires an archived mapping and impact review.

## Versioning

Reference changes that preserve meaning may increment catalogue documentation without changing the manifest schema. Renames semantic changes field changes or incompatible removals require a new schema version. v1 reference files remain intact while v2 is active.

## Validator consumption

The validator receives an explicit schema version. Version 1 reads the existing v1 JSON and equipment CSV. Version 2 reads `schema-version.json` plus every required v2 CSV. It verifies file presence columns lifecycle status unique values unique slugs snake_case slugs and deterministic sort order before validating manifest rows.

The validator must never infer a schema from whichever files happen to exist.
