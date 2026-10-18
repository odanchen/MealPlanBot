package app.mealplan.domain.measurement;
import java.math.BigDecimal;
import java.util.Map;
public final class UnitRegistry {
    public record Unit(String dimension, String baseUnit, BigDecimal baseFactor) {}
    private static Unit unit(String dimension,String base,String factor) { return new Unit(dimension,base,new BigDecimal(factor)); }
    private static final Map<String,Unit> UNITS=Map.ofEntries(
        Map.entry("g",unit("weight","g","1")), Map.entry("kg",unit("weight","g","1000")),
        Map.entry("oz",unit("weight","g","28.349523125")), Map.entry("lb",unit("weight","g","453.59237")),
        Map.entry("ml",unit("volume","ml","1")), Map.entry("l",unit("volume","ml","1000")),
        Map.entry("us-cup",unit("volume","ml","236.5882365")), Map.entry("us-tbsp",unit("volume","ml","14.78676478125")),
        Map.entry("us-tsp",unit("volume","ml","4.92892159375")), Map.entry("us-fl-oz",unit("volume","ml","29.5735295625")),
        Map.entry("count",unit("count","count","1")), Map.entry("clove",unit("count-clove","clove","1")),
        Map.entry("package",unit("package","package","1")), Map.entry("can",unit("package","can","1")),
        Map.entry("tin-156ml",unit("package-tin-156ml","tin-156ml","1")),
        Map.entry("bunch",unit("package","bunch","1")));
    private UnitRegistry() {}
    public static Map<String,Unit> definitions() { return UNITS; }
    public static Unit require(String id) {
        var result=UNITS.get(id); if(result==null) throw new IllegalArgumentException("Unsupported unit"); return result;
    }
}
