package app.mealplan.content;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class ContentValidationTest {
 @TempDir Path directory;
 private Path copy() throws Exception {var source=ContentLoader.defaultRoot();var root=directory.resolve("content");try(var files=Files.walk(source)){for(var file:files.toList()){var destination=root.resolve(source.relativize(file));if(Files.isDirectory(file))Files.createDirectories(destination);else Files.copy(file,destination);}}return root;}
 @Test void invalidRevisionNeverPublishesAndAcceptedChangesPropagateTogether() throws Exception {
  var root=copy();var loader=new ContentLoader(new MockEnvironment().withProperty("mealplan.content-path",root.toString()));var before=loader.snapshot();
  var file=root.resolve("recipes/red-wine-beef-stew-with-mashed-potatoes/recipe.yaml");String source=Files.readString(file);
  Files.writeString(file,source.replace("Red Wine Beef Stew with Mashed Potatoes","Updated stew").replace("\"1.4\"","\"1.6\"").replace("Pat the beef dry","Dry the beef carefully"));
  var after=loader.reload();assertThat(after.revision()).isNotEqualTo(before.revision());
  var projection=(java.util.Map<?,?>)new app.mealplan.web.RecipeController(loader).detail("red-wine-beef-stew-with-mashed-potatoes");assertThat(projection.get("revision")).isEqualTo(after.revision());assertThat(((RecipeDocument)projection.get("recipe")).title()).isEqualTo("Updated stew");var recipe=after.recipes().get("red-wine-beef-stew-with-mashed-potatoes");assertThat(recipe.title()).isEqualTo("Updated stew");assertThat(recipe.ingredients().getFirst().quantity()).isEqualTo("1.6");assertThat(recipe.sections().getFirst().steps().getFirst().content().toString()).contains("Dry the beef carefully");
  Files.writeString(file,Files.readString(file)+"\ntitle: duplicate\n");assertThatThrownBy(loader::reload).isInstanceOf(IllegalArgumentException.class);assertThat(loader.snapshot()).isSameAs(after);assertThat(before.recipes().get(recipe.id()).title()).startsWith("Red Wine");
 }
 @Test void rejectsUnsafeOrIncompleteAuthoring() throws Exception {
  var root=copy();var file=root.resolve("recipes/red-wine-beef-stew-with-mashed-potatoes/recipe.yaml");String source=Files.readString(file);
  for(String invalid:new String[]{source+"\nunknownField: true\n",source.replace("Pat the beef dry","<script>alert(1)</script>"),source.replace("\"1.4\"","1.4"),source.replace("\"1.4\"","\"0\""),source.replace("unit: \"kg\"","unit: \"unsupported\""),source.replace("term: \"braise\"","term: \"unknown\""),source.replace("images: []","images:\n  - path: ../../outside.jpg\n    alt: Outside"),source.replace("images: []","images:\n  - path: missing.jpg\n    alt: Missing"),source.replace("images: []","images:\n  - path: image.jpg"),source.replace("title: \"Red Wine Beef Stew with Mashed Potatoes\"","title: !!java/object 'unsafe'")}){
   Files.writeString(file,invalid);assertThatThrownBy(()->ContentLoader.load(root)).as(invalid.substring(0,Math.min(invalid.length(),80))).isInstanceOf(Exception.class);
  }
 }
 @Test void aliasCollisionsAndImageSymlinksFail() throws Exception {
  var root=copy();var ingredients=root.resolve("ingredients.yaml");String source=Files.readString(ingredients);Files.writeString(ingredients,source.replace("name: \"carrots\"","name: \"onions\""));assertThatThrownBy(()->ContentLoader.load(root)).isInstanceOf(IllegalArgumentException.class);Files.writeString(ingredients,source);
  var dir=root.resolve("recipes/red-wine-beef-stew-with-mashed-potatoes");Files.createSymbolicLink(dir.resolve("image.jpg"),ingredients);var file=dir.resolve("recipe.yaml");Files.writeString(file,Files.readString(file).replace("images: []","images:\n  - path: image.jpg\n    alt: Image"));assertThatThrownBy(()->ContentLoader.load(root)).isInstanceOf(IllegalArgumentException.class);
 }

 @Test void optionalImagesAreServedOnlyThroughValidatedRecipeAssets() throws Exception {
  var root=copy();var directory=root.resolve("recipes/red-wine-beef-stew-with-mashed-potatoes");
  var file=directory.resolve("recipe.yaml");Files.writeString(file,Files.readString(file).replace("images: []","images:\n  - path: rice.png\n    alt: Cooked rice"));
  byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jhX8AAAAASUVORK5CYII=");Files.write(directory.resolve("rice.png"),png);
  var loader=new ContentLoader(new MockEnvironment().withProperty("mealplan.content-path",root.toString()));
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new app.mealplan.web.RecipeController(loader)).setControllerAdvice(new app.mealplan.web.ApiErrors()).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/recipes/red-wine-beef-stew-with-mashed-potatoes/images/rice.png")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(png));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/recipes/red-wine-beef-stew-with-mashed-potatoes/images/other.png")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
 }
}
