# Alias Strategy

Aliases improve findability without multiplying canonical records.

## Alias classes

- `search_aliases`: common, informal, abbreviated, or legacy search terms.
- `manufacturer_aliases`: brand or product terminology.
- `regional_aliases`: geographically specific English terms pending wider localisation design.

Values are separated by `|`. Aliases should be concise names, not sentences, coaching cues, translations, or keyword stuffing.

## Rules

- Manufacturer names never become canonical names.
- An alias may not exactly match another canonical name, case-insensitively.
- Avoid repeating the canonical name in an alias field.
- The same alias assigned to multiple candidates requires an explicit ambiguity review.
- Spelling variants belong here only when users plausibly search for them.
- Internationalised display names should remain in the application's localisation system, not be improvised as aliases.

Renaming a canonical candidate requires an alias and redirect decision during production mapping; the catalogue workspace itself does not define runtime redirects.
