package app.mealplan.domain.measurement;

import java.math.*;

/** Display-only equivalences: four significant digits keep relative error below 1%. */
public final class MeasurementDisplay {

    public record Display(String quantity, String unit, String label) {}

    private MeasurementDisplay() {}

    public static Display display(Quantity value, String system) {
        var result = display(value.decimalText(), value.unit(), system);
        return value.packageSize() == null
            ? result
            : new Display(
                  result.quantity(),
                  result.unit(),
                  result.label() + " (" + value.packageSize() + ")"
              );
    }

    public static Display display(String decimal, String unit, String system) {
        if (!"metric".equals(system) && !"us".equals(system)) throw new IllegalArgumentException(
            "Choose metric or us measurements"
        );
        var definition = UnitRegistry.require(unit);
        var amount = new BigDecimal(decimal);
        if (amount.signum() <= 0) throw new IllegalArgumentException("Quantity must be positive");
        String target = unit;
        var base = amount.multiply(definition.baseFactor());
        if (definition.dimension().equals("weight")) target = system.equals("metric")
            ? base.compareTo(new BigDecimal("1000")) >= 0
                ? "kg"
                : "g"
            : base.compareTo(UnitRegistry.require("lb").baseFactor()) >= 0
              ? "lb"
              : "oz";
        if (definition.dimension().equals("volume")) target = system.equals("metric")
            ? base.compareTo(new BigDecimal("1000")) >= 0
                ? "l"
                : "ml"
            : base.compareTo(UnitRegistry.require("us-cup").baseFactor()) >= 0
              ? "us-cup"
              : base.compareTo(UnitRegistry.require("us-tbsp").baseFactor()) >= 0
                ? "us-tbsp"
                : "us-tsp";
        boolean convertible =
            definition.dimension().equals("weight") || definition.dimension().equals("volume");
        String text = convertible
            ? base
                  .divide(UnitRegistry.require(target).baseFactor(), MathContext.DECIMAL128)
                  .round(new MathContext(4, RoundingMode.HALF_UP))
                  .stripTrailingZeros()
                  .toPlainString()
            : amount.stripTrailingZeros().toPlainString();
        return new Display(text, target, UnitRegistry.label(target));
    }
}
