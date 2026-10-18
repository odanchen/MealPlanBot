package app.mealplan.content;

import app.mealplan.domain.measurement.MeasurementDisplay;
import java.util.*;

/** Curated aliases are exact normalized matches; food forms never merge through fuzzy matching. */
public final class IngredientCatalogue {

    private final Map<String, String> aliases = new HashMap<>();

    public IngredientCatalogue(ContentSnapshot snapshot) {
        snapshot.ingredients().forEach((id, ingredient) -> {
            var names = new ArrayList<>(ingredient.aliases());
            names.add(ingredient.name());
            for (String name : names) {
                var previous = aliases.putIfAbsent(normalize(name), id);
                if (previous != null && !previous.equals(id)) throw new IllegalArgumentException(
                    "Ingredient alias collision"
                );
            }
        });
    }

    public static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public String resolve(String name) {
        return name == null ? null : aliases.get(normalize(name));
    }

    public record IngredientView(
        String ingredientId,
        String name,
        String category,
        String quantity,
        String unit,
        String displayQuantity,
        String displayUnit,
        String displayUnitLabel,
        boolean optional,
        String note
    ) {}

    public static List<IngredientView> project(
        ContentSnapshot snapshot,
        RecipeDocument recipe,
        String system
    ) {
        return recipe
            .ingredients()
            .stream()
            .map(i -> {
                var item = snapshot.ingredients().get(i.ingredientId());
                var display = MeasurementDisplay.display(i.quantity(), i.unit(), system);
                String category =
                    item == null ||
                    item.category() == null ||
                    !snapshot.categories().contains(item.category())
                        ? "other"
                        : item.category();
                return new IngredientView(
                    i.ingredientId(),
                    item == null ? i.ingredientId() : item.name(),
                    category,
                    i.quantity(),
                    i.unit(),
                    display.quantity(),
                    display.unit(),
                    display.label(),
                    i.optional(),
                    i.note()
                );
            })
            .toList();
    }
}
