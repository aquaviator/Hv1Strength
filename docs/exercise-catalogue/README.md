# Human Strength Exercise Catalogue Workspace

This repository workspace supports the deterministic design, review, and audit of candidate exercise records. It is a content architecture layer, not a production database, Android redesign, Firestore import, or Room migration.

## Boundaries

- Candidate rows are proposals and are never authoritative until reviewed.
- `catalogue_key` values are stable planning identifiers such as `demo_pushup`; they are not production UUIDs.
- No file here is production JSON or an instruction to populate Firestore or Room.
- Canonical names are manufacturer-neutral. Brand, regional, and informal names belong in alias fields.
- The demonstration rows exist only to exercise the architecture and validator.

## Layout

- `docs/exercise-catalogue/`: architecture, governance, schema alignment, export boundary, review, and release documents.
- `catalogue/candidate-manifest.csv`: candidate manifest with demonstration records only.
- `catalogue/reference/`: controlled values and equipment reference data.
- `catalogue/families/`: header-only family work queues.
- `tools/catalogue/validate_catalogue.py`: standard-library validator and Markdown audit generator.
- `tests/`: validator regression tests and deliberately invalid fixture.

## Quick start

From the Android repository root:

```text
python tools/catalogue/validate_catalogue.py --schema-version 1
python tools/catalogue/validate_catalogue.py --schema-version 2
python -m unittest discover -s tests -p "test_validate_catalogue*.py" -v
```

Schema selection is explicit: v1 reads the frozen demonstration manifest and original references, while v2 reads the empty v2 authority and versioned references. The validator writes `catalogue/catalogue-audit.md` unless `--report` selects another destination. A non-zero exit code means one or more errors were found. Warnings require review but do not by themselves fail validation.

## Authoring conventions

- CSV files use UTF-8, comma delimiters, and one header row.
- Multi-value cells use a vertical bar (`|`) separator.
- Controlled values are case-sensitive.
- Empty optional values are represented by an empty cell, never invented placeholders.
- Family files remain planning surfaces; the manifest is the single candidate index.
- Sort candidate rows deterministically by `catalogue_key` before review or release.

Read `docs/exercise-catalogue/REVIEW-PROTOCOL.md` before changing review states. Repository-specific evidence and mapping decisions are recorded in `docs/exercise-catalogue/SCHEMA-ALIGNMENT.md`.
