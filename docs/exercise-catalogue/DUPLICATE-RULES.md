# Duplicate Rules

## Exact duplicates

`catalogue_key` and `canonical_name` must each be unique. Name comparison ignores case and surrounding whitespace. An alias matching any canonical name is an error because it creates ambiguous identity.

## Near duplicates

The validator normalises punctuation, spacing, and common word order, then uses string similarity to flag likely near matches. A warning is a review prompt, not automatic proof of duplication.

Reviewers compare:

- movement mechanics and range;
- loading profile and resistance curve;
- stability and technical demand;
- training purpose;
- progression, regression, and rehabilitation role;
- durable user search expectation; and
- whether the difference is only tempo, pause, grip, stance, setup, branding, or wording.

## Resolution

- Keep one canonical row and merge discoverability terms into aliases when the distinction is nominal.
- Keep separate candidates only when the canonical-record rule is met and document the reason.
- Use `Merged as Duplicate` for the rejected row when preserving review history.
- Never reuse the retired `catalogue_key` for a different concept.

Minor setup variants may be expressed later as parameters or coaching guidance, but this workspace does not design those runtime structures.
