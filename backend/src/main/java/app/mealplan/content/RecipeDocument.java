package app.mealplan.content;
import java.util.*;
/** Authoring model; instruction segments remain plain data, never markup. */
public record RecipeDocument(int schemaVersion,String id,String title,Servings servings,Timing timing,String preparationGuidance,List<Image> images,List<Ingredient> ingredients,List<Section> sections) {
 public record Servings(int estimate,String note) {}
 public record Timing(Integer prepMinutes,Integer cookMinutes,String guidance) {}
 public record Image(String path,String alt) {}
 public record Ingredient(String ingredientId,String quantity,String unit,boolean optional,String note) {}
 public record Section(String id,String title,List<Step> steps) {public Section {steps=List.copyOf(steps);}}
 public record Step(String id,List<Map<String,Object>> content) {public Step {content=content.stream().map(Map::copyOf).toList();}}
 public RecipeDocument {images=List.copyOf(images);ingredients=List.copyOf(ingredients);sections=List.copyOf(sections);}
}
