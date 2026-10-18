package app.mealplan.web;
import app.mealplan.shopping.*;
import java.security.Principal;
import java.util.Map;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/lists")
public class ShoppingListController {
 private final ShoppingListService service;
 public ShoppingListController(ShoppingListService service){this.service=service;}
 public record Create(String name) {}
 @GetMapping public Object catalogue(Principal p){return Map.of("lists",service.catalogue(p.getName()));}
 @PostMapping public ResponseEntity<ShoppingList> create(Principal p,@RequestBody Create input,@RequestHeader(value="Idempotency-Key",required=false) String key){var result=service.create(p.getName(),input.name(),key);return ResponseEntity.created(URI.create("/api/lists/"+result.id())).eTag(Long.toString(result.version())).body(result);}
 @GetMapping("/{id}") public ResponseEntity<ShoppingList> detail(Principal p,@PathVariable String id){var result=service.get(p.getName(),id);return ResponseEntity.ok().eTag(Long.toString(result.version())).body(result);}
}
