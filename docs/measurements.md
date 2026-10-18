# Ingredient display and shopping views

Canonical quantities remain exact positive decimal strings. Display switches do
not update recipes or saved purchases. Weight uses g/kg or avoirdupois oz/lb;
volume uses ml/l or US teaspoons/tablespoons/cups. A US fluid ounce is volume,
while oz is weight. Exact factors are defined once in UnitRegistry:

- 1 oz = 28.349523125 g; 1 lb = 453.59237 g.
- 1 US cup = 236.5882365 ml; 1 US tablespoon = 14.78676478125 ml.
- 1 US teaspoon = 4.92892159375 ml; 1 US fluid ounce = 29.5735295625 ml.

Metric display chooses kg/l at 1000 base units, otherwise g/ml. US display chooses
lb at one pound, otherwise oz; cups at one cup, tablespoons at one tablespoon,
otherwise teaspoons. Converted values round once to four significant digits using
HALF_UP, keeping relative display error below 1%. Aggregation always precedes this
rounding. Counts, cloves, bunches and explicit package sizes retain their units;
there is no inferred density, yield, package or count conversion.

Curated aliases resolve after case/whitespace normalization and cannot identify
multiple ingredients. Fresh tomato and tomato paste are distinct products. Recipe
groups retain every ingredient, with an other fallback for missing categories.

Shopping order is alphabetical ascending (SQLite NOCASE) or chronological oldest
first. Creation time and UUID break ties. Bought grouping independently places
unchecked rows first. Views never merge entries or change quantities, creation
time, bought state or archive eligibility, and require no timestamp display.
