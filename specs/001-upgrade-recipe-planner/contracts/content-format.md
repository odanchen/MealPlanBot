# Authoritative Content Contract

Status: design contract; example content is illustrative, not a migrated tutorial.

## Layout and publication

```text
content/
  glossary.yaml
  ingredients.yaml
  schedules/three-week.yaml
  recipes/<stable-recipe-slug>/
    recipe.yaml
    images/<asset-name>.jpg
```

Each recipe has exactly one YAML content file. Shared catalogues are not repeated per recipe. Files contain no full HTML page, scripts, CSS, or component code. Schema version 1 uses YAML 1.2-compatible scalar rules; safe parsing rejects duplicate keys, custom object tags, unknown fields, and unbounded aliases. Quantities are quoted decimal strings, parsed as BigDecimal. IDs are case-sensitive stable slugs; display labels can change without changing references.

Deploy a complete validated content revision with the release. The backend loads an immutable validated snapshot and exposes structured JSON. The frontend does not parse YAML. Production content becomes accepted on successful release activation; edits cannot require manually correcting several HTML copies. A generated occurrence uses one snapshot and stores its revision. Public recipe APIs may cache only with revision-aware validation; private data is never public-cacheable.

## Shared glossary

`content/glossary.yaml`:

```yaml
schemaVersion: 1
terms:
  saute:
    label: Sauté
    definition: Cook food quickly in a little fat over moderately high heat, stirring or tossing.
  simmer:
    label: Simmer
    definition: Cook liquid gently so small bubbles rise occasionally rather than boiling vigorously.
```

A term has one stable ID, nonblank canonical label, and nonblank plain-text definition. Definitions are concise shared content and do not contain executable markup or nested term references in version 1. Definition changes apply to every use without a local explanation override on the next accepted content revision; overridden and standalone explanations remain unchanged. Optional future glossary search is not part of this feature.

## Recipe example

`content/recipes/simple-rice/recipe.yaml`:

```yaml
schemaVersion: 1
id: simple-rice
title: Simple rice
servings:
  estimate: 4
  note: As a side dish
timing:
  prepMinutes: 5
  cookMinutes: 20
preparationGuidance: Rinse the rice before cooking.
images:
  - path: images/rice.jpg
    alt: Cooked rice in a serving bowl
ingredients:
  - ingredientId: dry-rice
    quantity: "200"
    unit: g
    optional: false
    note: Rinsed
  - ingredientId: water
    quantity: "400"
    unit: ml
    optional: false
    note: ""
sections:
  - id: method
    title: Method
    steps:
      - id: cook
        content:
          - text: Bring the rice and water to a boil, then reduce the heat and
          - text: " "
          - term: simmer
            label: simmer
          - text: " until tender."
```

Instruction `content` is an ordered array of discriminated segments:

- `{text: string}` renders plain text exactly, including authored whitespace. It is not interpreted as HTML or Markdown.
- `{term: termId, label?: string, explanation?: string}` renders an explanation control. Without `explanation`, it uses the glossary definition. With `explanation`, it uses that occurrence-specific override. Omitted `label` uses the canonical glossary label.
- `{label: string, explanation: string}` renders a standalone explanation control without a glossary reference. Both fields must be nonblank. It does not create or modify a glossary entry.
- `{recipe: recipeId, label?: string, section?: sectionId}` renders a recipe link, optionally to a section. Omitted label uses the destination title.

Each segment must match exactly one shape. `text` and `recipe` cannot be mixed with explanation-control fields. An explicitly supplied `term` must resolve even when `explanation` is present; omit `term` for a standalone explanation. Explicit nulls and blank labels/overrides are invalid rather than implicit fallbacks. Display label resolves as local label then glossary label; explanation resolves as local explanation then glossary definition. At least one source of each must exist. Version 1 uses segments for instruction steps and plain strings for metadata/ingredient notes. To avoid two representations for the same field, `preparationGuidance` in version 1 is plain text and step `content` always uses segments. No text is auto-annotated: authors choose precisely which occurrences need explanations. An instruction-only link never expands ingredients automatically.

For a rice reference in another recipe:

```yaml
content:
  - text: "Prepare the rice using "
  - recipe: simple-rice
    label: the basic rice method
    section: method
  - text: "."
```

The parent recipe must list its own required rice quantity. Navigation does not add the linked method's ingredients again.

## Explanation overrides

```yaml
# Shared glossary definition and canonical label
- term: saute

# Shared term with a recipe-specific explanation; canonical label retained
- term: saute
  explanation: Cook the onions gently in a little oil until translucent for this dish.

# Standalone annotation: no term name or glossary entry required
- label: onion cooking stage
  explanation: Stop cooking when the onions become translucent, before they brown.
```

`explanation` is an optional plain-text override belonging to that exact occurrence in this recipe YAML. It never writes back to the glossary or other occurrences, including another occurrence of the same term in this recipe. Shared definition updates affect only uses that inherit them. Neither override nor standalone text accepts executable markup or nested term controls. All three forms share the same CookingTerm component and bubble behavior. Backend JSON supplies resolved label/explanation, optional glossary ID, and the content revision; the frontend does not choose a competing definition.

## Frontend behavior

One RecipePage renders metadata, categorized ingredients, image assets, section/step components, and InlineContent segments. One CookingTerm component renders every glossary-backed, overridden, and standalone explanation consistently. It shows the definition on pointer hover or keyboard focus; click/tap pins it open, and another click, outside click, or Escape dismisses it. While pinned, pointer movement does not close it; after dismissal it does not immediately reopen merely because focus remains. Moving into the bubble keeps it available. Keyboard focus remains on the trigger; the trigger has an accessible name and associated description. The definition has no interactive nested content in version 1.

Place the bubble above the term when space permits, flip or shift it at viewport boundaries, and constrain its width at 320 CSS pixels. The explanation must not rely on color or hover alone. Screen-reader users can obtain the definition without pointer input. Avoid opening several bubbles at once. A plain unannotated word remains plain text even if it matches a glossary label.

Recipe links are ordinary application links with meaningful labels and a usable back path. Optional images require alternative text, use paths contained within the recipe directory, and cannot escape it through traversal or symlinks. No image means no broken placeholder.

## Ingredient and schedule catalogues

Ingredient catalogue: stable ingredient ID, canonical display name, curated singular/plural aliases, category ID, optional explicitly known conversion metadata. Aliases resolve uniquely after case/whitespace normalization; collisions fail validation. Categories initially include spices, liquids, meats, vegetables, and other. Different product forms have distinct IDs. Units include existing purchase units plus documented metric/US weight and volume units; explicit counts/packages remain distinct.

Meal schedule template: stable content ID, title, cycle length, and ordered day slots referencing recipe IDs or rest days, without an absolute calendar anchor. The sole supplied template has 21 slots; it is authenticated catalogue content, not a hardcoded global schedule. The calendar anchor belongs to a user’s configured schedule in SQLite. Any calendar weekday is valid for the personal anchor; it identifies slot 1 and the first active date. Dates before it have no assigned slot and show not_started. Recipe/rest-day slots are relative positions, not fixed calendar weekdays. The supplied template has five meals then two rest days per seven slots, repeated three times. Validate that its declared cycle length matches its slot count. Every slot references the recipe’s stable slug directly. Cooking-slot positions are schedule ordering, not recipe identities. Generic method recipes use the same identity scheme without requiring a rotation slot. No second numeric identity or old-ID mapping is stored or exposed.

## Validation and acceptance

CI and the operator validator reject missing required fields, invalid/duplicate IDs, nonpositive quantities, unsupported units, bad references, missing section anchors, missing images, unsafe paths, missing alt text, invalid categories, missing definitions for referenced glossary entries, unresolved explicit term IDs even with an override, empty explanation overrides, and standalone annotations lacking a label or explanation. All fifteen tutorials must be compared for methods, ingredient quantities, preparation notes, and serving estimates. Recurring generation fails as a whole if any required recipe is unavailable; it cannot emit a partial batch.

A glossary example test changes the definition of `simmer` once and checks two inheriting recipes receive the same changed definition while labels remain independently authored; an overridden occurrence keeps its local explanation and a standalone occurrence requires no glossary entry. Additional checks cover inflected labels, repeated terms, plain matching words, keyboard/touch dismissal, narrow layouts, and linked recipe ingredient nonduplication.
