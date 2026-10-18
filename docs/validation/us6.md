# User story 6 verification

Implemented T083–T090 on 2026-10-18. Requirements checklist remains 16 checked,
zero unchecked. No implementation extension hooks are configured.

Recipe responses preserve authoritative ingredient quantities/units and add a
separate selected-system display projection. MeasurementDisplay uses the shared
exact unit factors and rounds only the final display to four significant digits.
Metric/US weight and volume conversions stay within 1% of the exact equivalent.
US cups, tablespoons, teaspoons, fluid ounces and weight ounces are explicitly
labelled; counts, bunches, cloves and package sizes keep their meaning. No density,
yield, count-to-weight or package-size equivalence is guessed. Rules and factors
are documented in `docs/measurements.md`.

IngredientCatalogue shares exact normalized alias lookup with manual additions,
keeps different product forms separate and projects canonical names/categories.
IngredientGroups renders every ingredient once with an other fallback for missing
or unknown categories. The metric/US selector requests the same stable recipe API;
it changes no content or purchase rows.

ShoppingEntryQueries provides deterministic alphabetical ascending or chronological
oldest-first ordering, with creation-time/UUID ties. Bought grouping independently
puts unchecked entries first. ShoppingViewControls changes only the read query;
entry IDs, quantities, bought state, age eligibility and focus remain intact.

Verification:

- Full `./scripts/check.sh` passes: 69 backend tests, 13 frontend tests, 11 Chromium
  journeys, lint/typecheck, validated content and executable JAR packaging.
- Conversion examples exercise ten supported weight/volume units, six magnitudes
  (including very small/large values), and both measurement systems. Relative
  display error is at most 1%; the original Quantity object remains unchanged.
- Exact 1 kg + 500 g aggregation displays 1.5 kg. Tomato/tomatoes resolve to the
  same canonical ID, while tomato paste remains distinct. Different package sizes,
  counts and volume remain separate; explicit package text is retained.
- All three seven-day plans aggregate to precisely the same ingredient/unit totals
  as the complete 21-day rotation using the shared generation planner. US5's method
  isolation test still confirms links add no extra ingredients.
- Recipe HTTP tests verify metric/US selection, unsupported-system rejection and
  identical canonical ingredient payloads across display systems.
- Component tests retain all categorized/uncategorized entries and canonical values.
- Actual-SQLite view tests check every alphabetical/chronological × grouped/ungrouped
  combination, quantities/bought states and UUID ordering for identical name/time ties.
- Chromium switches recipe measurements in both directions, verifies canonical
  quantities/count/package labels and equivalent US measures. A second journey checks
  all four shopping view combinations, immediate live refresh, focus retention,
  unchanged saved entry payloads and absence of required timestamps.
- Every earlier recipe, glossary, personal schedule, private/shared shopping,
  invitation, recurring generation, source deletion and archive journey still passes.

No dependency locks, checklist markers, authoritative recipe quantities or production
state changed. Real Pi/Google/participant acceptance and release/deployment work
remain the explicitly separate operational tasks T091–T102; no deployment ran.
