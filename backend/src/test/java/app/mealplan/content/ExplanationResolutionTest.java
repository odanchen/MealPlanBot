package app.mealplan.content;
import java.util.*;
import java.time.LocalDate;
import app.mealplan.domain.schedule.ShoppingCoveragePlanner;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ExplanationResolutionTest {
 @Test void overridesAndStandaloneAnnotationsAreIsolatedFromSharedUpdates() throws Exception {
  var snapshot=ContentLoader.load(ContentLoader.defaultRoot());var shared=Map.<String,Object>of("term","simmer","label","simmering");var override=Map.<String,Object>of("term","simmer","label","simmer","explanation","Keep this beef barely bubbling.");var standalone=Map.<String,Object>of("label","Fluff","explanation","Separate the grains gently.");
  assertThat(ExplanationResolver.resolve(shared,snapshot).get("explanation")).isEqualTo(snapshot.terms().get("simmer").definition());
  var terms=new HashMap<>(snapshot.terms());terms.put("simmer",new ContentSnapshot.Term("Simmer","Changed shared definition."));var changed=new ContentSnapshot(snapshot.revision(),snapshot.root(),snapshot.recipes(),snapshot.templates(),snapshot.categories(),snapshot.ingredients(),terms);
  assertThat(ExplanationResolver.resolve(shared,changed).get("explanation")).isEqualTo("Changed shared definition.");assertThat(ExplanationResolver.resolve(shared,changed).get("label")).isEqualTo("simmering");assertThat(ExplanationResolver.resolve(override,changed).get("explanation")).isEqualTo("Keep this beef barely bubbling.");assertThat(ExplanationResolver.resolve(standalone,changed)).isEqualTo(standalone);
 }
 @Test void explicitMissingTermsAndInvalidShapesAreRejected() throws Exception {
  var snapshot=ContentLoader.load(ContentLoader.defaultRoot());var invalid=new ArrayList<Map<String,Object>>();invalid.add(Map.of("term","missing","explanation","Override"));invalid.add(Map.of("label","Only label"));invalid.add(Map.of("explanation","Only explanation"));invalid.add(Map.of("term","simmer","label"," "));invalid.add(Map.of("term","simmer","explanation",""));invalid.add(Map.of("term","simmer","text","mixed"));var nullOverride=new HashMap<String,Object>();nullOverride.put("term","simmer");nullOverride.put("explanation",null);invalid.add(nullOverride);
  for(var segment:invalid)assertThatThrownBy(()->ExplanationResolver.resolve(segment,snapshot)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void methodLinksNeverExpandPurchasingIngredients() throws Exception {
  var snapshot=ContentLoader.load(ContentLoader.defaultRoot());var planner=new ShoppingCoveragePlanner();var anchor=LocalDate.of(2026,10,20);var plan=planner.plan(snapshot,"three-week",anchor,anchor,anchor.plusDays(21),null);
  var recipes=new LinkedHashMap<>(snapshot.recipes());recipes.remove("simple-rice");var noMethod=new ContentSnapshot(snapshot.revision(),snapshot.root(),recipes,snapshot.templates(),snapshot.categories(),snapshot.ingredients(),snapshot.terms());assertThat(planner.plan(noMethod,"three-week",anchor,anchor,anchor.plusDays(21),null).ingredients()).isEqualTo(plan.ingredients());
 }
}
