# Authoritative ingredients

Each `content/recipes/<slug>/recipe.yaml` contains one ingredient list with stable
`ingredientId`, a quoted positive decimal `quantity`, `unit`, optionality and notes.
`content/ingredients.yaml` defines canonical names, aliases and categories. Aliases
resolve uniquely after case and whitespace normalization; distinct product forms
remain separate. Tomato/tomatoes resolve to fresh tomatoes, independently of tomato paste.

The supplied quantities remain unchanged. Purchase units are `g`, `kg`, `ml`,
`count`, `bunch` and explicit `tin-156ml` (one whole 156 ml tin). A tin is a purchase
unit and is not converted into volume; herb bunches have no inferred mass.
The shared registry also describes supported metric and US units. No density,
pulse yield, whole-squash yield or pantry-stock deduction is inferred.

Preparation guidance retains the original optional/pantry treatment, divided
amounts, dry-pulse advance cooking and raw edible-weight descriptions. An optional
purchase already present in the supplied ingredient list remains present.
Instructions and glossary labels never introduce another editable purchasing list.

Run `./backend/gradlew -p backend validateContent` to check a revision and
`./scripts/check.sh` for migration, API, frontend and browser verification.
See the [content contract](../specs/001-upgrade-recipe-planner/contracts/content-format.md).
