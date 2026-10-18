package app.mealplan.content;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class ContentMigrationTest {
 @Test void allTutorialContentAndRelativeSlotsArePreserved() throws Exception {
  var baseline=new JsonMapper().readTree(getClass().getResourceAsStream("/content-baseline/recipes.json"));
  var snapshot=ContentLoader.load(ContentLoader.defaultRoot());
  assertThat(snapshot.recipes()).hasSize(16);
  var slugs=new ArrayList<String>();
  for(var original:baseline){
   String slug=original.get("slug").asString();slugs.add(slug);var recipe=snapshot.recipes().get(slug);
   assertThat(recipe.title()).isEqualTo(original.get("title").asString());
   assertThat(recipe.servings().note()).isEqualTo(original.get("facts").get(0).asString());
   assertThat(recipe.timing().guidance()).isEqualTo(original.get("facts").get(1).asString());
   assertThat(recipe.preparationGuidance()).isEqualTo(original.get("preparationGuidance").asString());
   assertThat(recipe.ingredients()).hasSize(original.get("ingredients").size());
   for(int i=0;i<recipe.ingredients().size();i++){
    var ingredient=recipe.ingredients().get(i);var old=original.get("ingredients").get(i);
    assertThat(snapshot.ingredients().get(ingredient.ingredientId()).name()).isEqualTo(old.get("name").asString());
    assertThat(ingredient.quantity()).isEqualTo(old.get("quantity").asString());
    String unit=old.get("unit").asString();assertThat(ingredient.unit()).isEqualTo(unit.equals("each")?"count":unit.equals("tin_156ml")?"tin-156ml":unit);
   }
   var steps=recipe.sections().getFirst().steps();assertThat(steps).hasSize(original.get("steps").size());
   for(int i=0;i<steps.size();i++)assertThat(steps.get(i).content().stream().map(s->(String)s.getOrDefault("text",s.get("label"))).reduce("",String::concat)).isEqualTo(original.get("steps").get(i).asString());
  }
  var expected=new ArrayList<String>();for(int week=0;week<3;week++){expected.addAll(slugs.subList(week*5,week*5+5));expected.add(null);expected.add(null);}
  assertThat(snapshot.templates().get("three-week").slots().stream().map(ScheduleTemplate.Slot::recipeId).toList()).containsExactlyElementsOf(expected);
 }
}
