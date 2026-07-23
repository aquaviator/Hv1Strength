# Equipment Taxonomy

Equipment values describe the principal implementation required by a candidate. Every value must exist in `catalogue/reference/equipment.csv`.

## Rules

- Use generic equipment classes, never manufacturers or product lines.
- Choose the equipment that materially defines execution; incidental supports do not need a new value.
- Use `Bodyweight` where the participant and environment provide the load.
- Use `None` only for an exercise requiring no meaningful equipment or external load.
- A machine type may justify an equipment variation when its path, resistance curve, stability, or user expectation is materially different.
- Do not create a canonical record merely because a different brand supplies equivalent equipment.
- Composite equipment may be represented by multiple values separated with `|`; each component must be referenced.

The initial reference list is deliberately conservative and expandable through reviewed pull requests. New values require a definition, examples, and a collision check against existing terms.
