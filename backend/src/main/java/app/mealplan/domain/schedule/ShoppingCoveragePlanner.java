package app.mealplan.domain.schedule;

import app.mealplan.content.*;
import app.mealplan.domain.measurement.Quantity;
import app.mealplan.domain.shopping.IngredientAggregator;
import java.time.*;
import java.util.*;

public final class ShoppingCoveragePlanner {

    public record Plan(
        LocalDate from,
        LocalDate to,
        List<MealDateResolver.Meal> meals,
        List<IngredientAggregator.IngredientAmount> ingredients
    ) {}

    public record IngredientView(String ingredientId, String name, String quantity, String unit) {}

    public record PlanView(
        LocalDate from,
        LocalDate to,
        List<MealDateResolver.Meal> meals,
        List<IngredientView> ingredients
    ) {}

    public static PlanView view(Plan plan, ContentSnapshot snapshot) {
        return new PlanView(
            plan.from(),
            plan.to(),
            plan.meals(),
            plan
                .ingredients()
                .stream()
                .map(i ->
                    new IngredientView(
                        i.ingredientId(),
                        snapshot.ingredients().get(i.ingredientId()).name(),
                        i.quantity().decimalText(),
                        i.quantity().unit()
                    )
                )
                .toList()
        );
    }

    public Plan plan(
        ContentSnapshot snapshot,
        String templateId,
        LocalDate anchor,
        LocalDate candidateFrom,
        LocalDate candidateTo,
        LocalDate coveredEnd
    ) {
        var template = snapshot.templates().get(templateId);
        if (template == null) throw new IllegalArgumentException("Missing schedule template");
        LocalDate from = candidateFrom.isBefore(anchor) ? anchor : candidateFrom;
        if (coveredEnd != null && from.isBefore(coveredEnd)) from = coveredEnd;
        if (!from.isBefore(candidateTo)) return new Plan(from, from, List.of(), List.of());
        var meals = from
            .datesUntil(candidateTo)
            .map(d -> new MealDateResolver().resolve(template, anchor, d))
            .toList();
        var input = new ArrayList<IngredientAggregator.IngredientAmount>();
        for (var meal : meals)
            if (meal.recipeId() != null) {
                var recipe = snapshot.recipes().get(meal.recipeId());
                if (recipe == null) throw new IllegalArgumentException("Missing required recipe");
                for (var i : recipe.ingredients()) {
                    if (
                        !snapshot.ingredients().containsKey(i.ingredientId())
                    ) throw new IllegalArgumentException("Missing ingredient");
                    input.add(
                        new IngredientAggregator.IngredientAmount(
                            i.ingredientId(),
                            new Quantity(i.quantity(), i.unit())
                        )
                    );
                }
            }
        return new Plan(from, candidateTo, meals, new IngredientAggregator().aggregate(input));
    }
}
