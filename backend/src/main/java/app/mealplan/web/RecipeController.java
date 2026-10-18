package app.mealplan.web;
import app.mealplan.content.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public final class RecipeController {
 private final ContentLoader content;
 public RecipeController(ContentLoader content){this.content=content;}
 @GetMapping("/api/recipes") public Object list(){var s=content.snapshot();return Map.of("revision",s.revision(),"recipes",java.util.stream.Stream.concat(s.rotation().stream(),s.recipes().keySet().stream()).distinct().map(s.recipes()::get).map(r->Map.of("id",r.id(),"title",r.title(),"url","/recipes/"+r.id())).toList());}
 @GetMapping("/api/recipes/{id}") public Object detail(@PathVariable String id){var s=content.snapshot();var r=s.resolvedRecipe(id);if(r==null)throw ApiErrors.notFound();var order=s.rotation();int index=order.indexOf(id);var result=new LinkedHashMap<String,Object>();result.put("revision",s.revision());result.put("recipe",r);result.put("ingredientCatalogue",s.ingredients());result.put("categories",s.categories());
  if(index>=0){result.put("previous",order.get((index+order.size()-1)%order.size()));result.put("next",order.get((index+1)%order.size()));}return result;
 }

 @GetMapping("/api/recipes/{id}/images/{*asset}") public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> image(@PathVariable String id,@PathVariable String asset) throws java.io.IOException {
  var snapshot=content.snapshot();var recipe=snapshot.recipes().get(id);String relative=asset.startsWith("/")?asset.substring(1):asset;
  if(recipe==null||recipe.images().stream().noneMatch(i->i.path().equals(relative)))throw ApiErrors.notFound();
  var root=snapshot.root().resolve("recipes").resolve(id);var path=root.resolve(relative).normalize();
  if(!path.startsWith(root)||java.nio.file.Files.isSymbolicLink(path)||java.nio.file.Files.isSymbolicLink(path.getParent())||!path.toRealPath().startsWith(root.toRealPath()))throw ApiErrors.notFound();
  String type=java.nio.file.Files.probeContentType(path);if(type==null||!java.util.Set.of("image/jpeg","image/png","image/webp","image/gif","image/avif").contains(type))throw ApiErrors.notFound();
  return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(type)).body(new org.springframework.core.io.FileSystemResource(path));
 }
}
