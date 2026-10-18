package app.mealplan.content;
import java.nio.file.Path;
import java.util.*;
public record ContentSnapshot(String revision,Path root,Map<String,RecipeDocument> recipes,Map<String,ScheduleTemplate> templates,List<String> categories,Map<String,Ingredient> ingredients,Map<String,Term> terms) {
 public record Ingredient(String name,List<String> aliases,String category) { public Ingredient {aliases=List.copyOf(aliases);} }
 public record Term(String label,String definition) {}
 public ContentSnapshot {
  recipes=Collections.unmodifiableMap(new LinkedHashMap<>(recipes));templates=Map.copyOf(templates);categories=List.copyOf(categories);ingredients=Map.copyOf(ingredients);terms=Map.copyOf(terms);
 }
 public List<String> rotation(){return templates.get("three-week").slots().stream().map(ScheduleTemplate.Slot::recipeId).filter(Objects::nonNull).distinct().toList();}
 public RecipeDocument resolvedRecipe(String id){
  var r=recipes.get(id);if(r==null)return null;
  var sections=r.sections().stream().map(section->new RecipeDocument.Section(section.id(),section.title(),section.steps().stream().map(step->new RecipeDocument.Step(step.id(),step.content().stream().map(segment->{
   var result=new LinkedHashMap<>(segment);
   if(!segment.containsKey("text")&&!segment.containsKey("recipe"))result.putAll(ExplanationResolver.resolve(segment,this));
   if(segment.containsKey("recipe"))result.putIfAbsent("label",recipes.get(segment.get("recipe")).title());
   return (Map<String,Object>)result;
  }).toList())).toList())).toList();
  return new RecipeDocument(r.schemaVersion(),r.id(),r.title(),r.servings(),r.timing(),r.preparationGuidance(),r.images(),r.ingredients(),sections);
 }
}
