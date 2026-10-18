package app.mealplan.domain.measurement;
import app.mealplan.domain.shopping.IngredientAggregator;
import app.mealplan.domain.shopping.IngredientAggregator.IngredientAmount;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class QuantityTest {
 private IngredientAmount item(String ingredient,String amount,String unit) {return new IngredientAmount(ingredient,new Quantity(amount,unit));}
 @Test void sumsExactlyBeforeDisplayRounding() {
  var results=new IngredientAggregator().aggregate(List.of(item("flour","1","kg"),item("flour","500","g"),item("milk","0.1","l"),item("milk","0.2","l")));
  assertThat(results.get(0).quantity().decimalText()).isEqualTo("1500"); assertThat(results.get(0).quantity().unit()).isEqualTo("g");
  assertThat(results.get(1).quantity().decimalText()).isEqualTo("300");
  assertThat(new Quantity("0.1000","g").decimalText()).isEqualTo("0.1");
  assertThat(new Quantity("1000","g").decimalText()).isEqualTo("1000");
 }
 @Test void usWeightAndVolumeFactorsAreExact() {
  var results=new IngredientAggregator().aggregate(List.of(item("milk","1","us-cup"),item("milk","1","us-tbsp"),item("milk","1","us-tsp"),item("beef","1","lb"),item("beef","1","oz")));
  assertThat(results.get(0).quantity().decimalText()).isEqualTo("256.303922875");
  assertThat(results.get(1).quantity().decimalText()).isEqualTo("481.941893125");
 }
 @Test void dimensionsProductsCountsAndPackageSizesStayDistinct() {
  var results=new IngredientAggregator().aggregate(List.of(item("tomato","2","count"),item("tomato","3","count"),item("tomato","100","g"),item("tomato","100","ml"),item("tomato-paste","100","g"),
   new IngredientAmount("beans",new Quantity("1","can","400 g")), new IngredientAmount("beans",new Quantity("2","can","400 g")),new IngredientAmount("beans",new Quantity("1","can","200 g"))));
  assertThat(results).hasSize(6);assertThat(results.get(0).quantity().decimalText()).isEqualTo("5");assertThat(results.get(4).quantity().decimalText()).isEqualTo("3");
 }
 @Test void rejectsNonpositiveAmbiguousOrNondecimalAmounts() {
  for(String value:new String[]{"0","-1","NaN","1e3","1,000","01",".5"," 2","2 "}) assertThatThrownBy(() -> new Quantity(value,"g")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> new Quantity("1","can")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> new Quantity("1","unknown")).isInstanceOf(IllegalArgumentException.class);
 }
}
