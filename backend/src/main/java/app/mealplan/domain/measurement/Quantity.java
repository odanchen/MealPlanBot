package app.mealplan.domain.measurement;

import java.math.BigDecimal;
import java.util.Objects;

/** Authoritative exact amount. Persist decimalText(), never a floating-point value. */
public record Quantity(BigDecimal amount, String unit, String packageSize) {
    public Quantity {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(unit);
        if (amount.signum() <= 0) throw new IllegalArgumentException("Quantity must be positive");
        amount = amount.stripTrailingZeros();
        if (packageSize != null && packageSize.isBlank()) throw new IllegalArgumentException(
            "Package size must be explicit"
        );
        var definition = UnitRegistry.require(unit);
        if (
            definition.dimension().equals("package") && packageSize == null
        ) throw new IllegalArgumentException("Package size is required");
        if (
            !definition.dimension().equals("package") && packageSize != null
        ) throw new IllegalArgumentException("Package size applies only to packages");
    }

    public Quantity(String decimal, String unit) {
        this(parse(decimal), unit, null);
    }

    public Quantity(String decimal, String unit, String packageSize) {
        this(parse(decimal), unit, packageSize);
    }

    private static BigDecimal parse(String value) {
        if (
            value == null || !value.matches("(?:0|[1-9][0-9]*)(?:[.][0-9]+)?")
        ) throw new IllegalArgumentException("Expected a positive decimal string");
        return new BigDecimal(value);
    }

    public String decimalText() {
        return amount.toPlainString();
    }
}
