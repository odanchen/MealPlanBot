package app.mealplan.domain.shopping;
import app.mealplan.domain.measurement.*;
import java.math.BigDecimal;
import java.util.*;
/** Callers resolve curated aliases to canonical ingredient IDs before aggregation. */
public final class IngredientAggregator {
    public record IngredientAmount(String ingredientId, Quantity quantity) {
        public IngredientAmount { if(ingredientId==null || ingredientId.isBlank()) throw new IllegalArgumentException("Ingredient ID required"); Objects.requireNonNull(quantity); }
    }
    private record Key(String ingredientId,String baseUnit,String packageSize) {}
    public List<IngredientAmount> aggregate(Collection<IngredientAmount> input) {
        var amounts=new LinkedHashMap<Key,BigDecimal>();
        for(var item:input) {
            var q=item.quantity(); var unit=UnitRegistry.require(q.unit());
            var key=new Key(item.ingredientId(),unit.baseUnit(),q.packageSize());
            amounts.merge(key,q.amount().multiply(unit.baseFactor()),BigDecimal::add);
        }
        return amounts.entrySet().stream().map(e -> new IngredientAmount(e.getKey().ingredientId(),
            new Quantity(e.getValue(),e.getKey().baseUnit(),e.getKey().packageSize()))).toList();
    }
}
