# Original purchase records (migration input)

Each entry in the retained `content/manifest.json` maps an original numeric ID
to an HTML tutorial and purchase JSON record in `content/recipes/`. All fifteen
pairs are preserved for migration. This document describes the original format;
the Java runtime does not yet load it. The new YAML content contract is in
[content-format.md](../specs/001-upgrade-recipe-planner/contracts/content-format.md).

A record has exactly two fields:

- `recipe_id`: integer 1–15, matching the manifest ID.
- `ingredients`: nonempty array (at most 200 entries), each with exactly `name`,
  `quantity`, and `unit`.

`name` is a plain string of 1–100 characters. The original records use specific raw purchase
names, such as `dry chickpeas`, `raw chicken`, and `tomato paste`. The normalized
name (lowercase with collapsed whitespace) is the aggregation key. No synonym,
plural, category, yield, density, or package-size inference occurs. Distinct
names remain distinct purchases. Authors must ensure entries are raw purchases;
a schema cannot establish whether arbitrary food names describe prepared food.
Do not include instructions, notes, cooked quantities or intermediate products.
Unknown fields are rejected.

`quantity` is a positive finite JSON number (not a string or boolean), up to 12
digits with at most 6 decimal places. `unit` is one of `g`, `kg`, `ml`, `l`, `each`,
`bunch`, or `tin_156ml`. These are the original validator
conventions, retained here to explain the source data. kg becomes g and l becomes ml using decimal
arithmetic. Each, bunch, weight, volume and tins stay separate, even for the same
ingredient. Use `each` for counted onions, for example.

Whenever `tomato paste` is present it must appear exactly once with quantity `1`
and unit `tin_156ml`: one whole 156 ml tin per recipe. Tins aggregate as tins, never
as a volume to round or convert. Any `tin_156ml` quantity must be whole.

The Python validation, preview and API implementations have been removed. Content
comparison tests and Java validation remain planned in T018–T033; this document
does not establish that the source records pass the new YAML schema.

The shipped tutorial ingredient lists use these supplied raw amounts directly,
including measured pantry ingredients. Optional items present in a record are
included in shopping totals; optional extras without entries are identified in
the tutorial. Pantry stock is not subtracted automatically. Dry beans are
measured before soaking/cooking; the tutorial uses their cooked batch without
assuming a fixed volume yield. Prepared raw squash is purchased by edible weight,
and whole star anise is counted as a purchase even though the recipe uses one point.
