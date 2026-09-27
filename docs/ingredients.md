# Raw purchase records

Each manifest entry maps an ID to a UTF-8 HTML page and a JSON record within
`content/recipes/`. All 15 real ingredient records are included. The separate
synthetic record in `tests/fixtures/` is only for tests; never copy test data into
the household menu. HTML still works independently of JSON.

A record has exactly two fields:

- `recipe_id`: integer 1–15, matching the manifest ID.
- `ingredients`: nonempty array (at most 200 entries), each with exactly `name`,
  `quantity`, and `unit`.

`name` is a plain string of 1–100 characters. Use consistent, specific raw purchase
names, such as `dry chickpeas`, `raw chicken`, and `tomato paste`. The normalized
name (lowercase with collapsed whitespace) is the aggregation key. No synonym,
plural, category, yield, density, or package-size inference occurs. Distinct
names remain distinct purchases. Authors must ensure entries are raw purchases;
a schema cannot establish whether arbitrary food names describe prepared food.
Do not include instructions, notes, cooked quantities or intermediate products.
Unknown fields are rejected.

`quantity` is a positive finite JSON number (not a string or boolean), up to 12
digits with at most 6 decimal places. `unit` is one of `g`, `kg`, `ml`, `l`, `each`,
`bunch`, or `tin_156ml`. Extend the validator deliberately for other units; never
silently guess a conversion. kg becomes g and l becomes ml using decimal
arithmetic. Each, bunch, weight, volume and tins stay separate, even for the same
ingredient. Use `each` for counted onions, for example.

Whenever `tomato paste` is present it must appear exactly once with quantity `1`
and unit `tin_156ml`: one whole 156 ml tin per recipe. Tins aggregate as tins, never
as a volume to round or convert. Any `tin_156ml` quantity must be whole.

Run `mealplan validate-ingredients` to check every recipe. It exits 2 and lists
all missing/invalid recipe IDs if any fail. `mealplan dry-run --date YYYY-MM-DD`
requires all five records for the next Sunday–Thursday and exits 2 without a
partial list otherwise. Validation details never echo file contents or secrets.

The API exposes validated raw purchases (no unit conversion); totals exist only
in shopping output. `ingredients_status` is `available`, `missing`, or `invalid`.

The shipped tutorial ingredient lists use these supplied raw amounts directly,
including measured pantry ingredients. Optional items present in a record are
included in shopping totals; optional extras without entries are identified in
the tutorial. Pantry stock is not subtracted automatically. Dry beans are
measured before soaking/cooking; the tutorial uses their cooked batch without
assuming a fixed volume yield. Prepared raw squash is purchased by edible weight,
and whole star anise is counted as a purchase even though the recipe uses one point.
