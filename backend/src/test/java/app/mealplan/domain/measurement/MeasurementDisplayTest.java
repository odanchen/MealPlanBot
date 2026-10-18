package app.mealplan.domain.measurement;

import static org.assertj.core.api.Assertions.*;

import app.mealplan.content.*;
import app.mealplan.domain.shopping.IngredientAggregator;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MeasurementDisplayTest {

    @Test
    void threeWeeklyPlansCombineIntoTheSameExactRotationTotals() throws Exception {
        var snapshot = ContentLoader.load(ContentLoader.defaultRoot());
        var planner = new app.mealplan.domain.schedule.ShoppingCoveragePlanner();
        var anchor = java.time.LocalDate.of(2026, 10, 20);
        var weekly = new ArrayList<IngredientAggregator.IngredientAmount>();
        for (int week = 0; week < 3; week++) weekly.addAll(
            planner
                .plan(
                    snapshot,
                    "three-week",
                    anchor,
                    anchor.plusDays(7L * week),
                    anchor.plusDays(7L * (week + 1)),
                    null
                )
                .ingredients()
        );
        assertThat(new IngredientAggregator().aggregate(weekly)).isEqualTo(
            planner
                .plan(snapshot, "three-week", anchor, anchor, anchor.plusDays(21), null)
                .ingredients()
        );
    }

    @Test
    void supportedEquivalentDisplaysStayWithinOnePercentAndNeverMutateTheSource() {
        for (String unit : List.of(
            "g",
            "kg",
            "oz",
            "lb",
            "ml",
            "l",
            "us-cup",
            "us-tbsp",
            "us-tsp",
            "us-fl-oz"
        ))
            for (String amount : List.of("0.00001", "0.1", "1", "12.345", "1000", "1234567"))
                for (String system : List.of("metric", "us")) {
                    var original = new Quantity(amount, unit);
                    var display = MeasurementDisplay.display(original, system);
                    var expected = original
                        .amount()
                        .multiply(UnitRegistry.require(unit).baseFactor());
                    var actual = new BigDecimal(display.quantity()).multiply(
                        UnitRegistry.require(display.unit()).baseFactor()
                    );
                    assertThat(
                        actual.subtract(expected).abs().divide(expected, MathContext.DECIMAL128)
                    ).isLessThanOrEqualTo(new BigDecimal("0.01"));
                    assertThat(original.amount()).isEqualByComparingTo(amount);
                    assertThat(original.unit()).isEqualTo(unit);
                }
    }

    @Test
    void exactAggregationAliasesAndIncompatiblePurchasingUnitsStayMeaningful() throws Exception {
        var snapshot = ContentLoader.load(ContentLoader.defaultRoot());
        var catalogue = new IngredientCatalogue(snapshot);
        assertThat(catalogue.resolve(" tomato ")).isEqualTo("fresh-tomatoes");
        assertThat(catalogue.resolve("TOMATOES")).isEqualTo("fresh-tomatoes");
        assertThat(catalogue.resolve("tomato paste")).isNotEqualTo(catalogue.resolve("tomato"));
        var input = List.of(
            new IngredientAggregator.IngredientAmount("tomato", new Quantity("1", "kg")),
            new IngredientAggregator.IngredientAmount("tomato", new Quantity("500", "g"))
        );
        var total = new IngredientAggregator().aggregate(input).getFirst().quantity();
        assertThat(MeasurementDisplay.display(total, "metric").quantity()).isEqualTo("1.5");
        var packages = new IngredientAggregator().aggregate(
            List.of(
                new IngredientAggregator.IngredientAmount(
                    "beans",
                    new Quantity("1", "can", "400 g")
                ),
                new IngredientAggregator.IngredientAmount(
                    "beans",
                    new Quantity("1", "can", "200 g")
                ),
                new IngredientAggregator.IngredientAmount("beans", new Quantity("1", "count")),
                new IngredientAggregator.IngredientAmount("beans", new Quantity("1", "ml"))
            )
        );
        assertThat(packages).hasSize(4);
        assertThat(
            MeasurementDisplay.display(new Quantity("1", "can", "400 g"), "us").label()
        ).isEqualTo("can (400 g)");
        assertThat(
            MeasurementDisplay.display(new Quantity("2", "bunch"), "metric").unit()
        ).isEqualTo("bunch");
    }
}
