package app.mealplan.web;
import app.mealplan.shopping.ListMembershipService;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class ListMembershipController {
 private final ListMembershipService service;
 public ListMembershipController(ListMembershipService service){this.service=service;}
 @DeleteMapping("/api/lists/{id}/membership") public ResponseEntity<Void> leave(Principal p,@PathVariable String id){service.leave(p.getName(),id);return ResponseEntity.noContent().build();}
 @DeleteMapping("/api/lists/{id}") public ResponseEntity<Void> delete(Principal p,@PathVariable String id,@RequestHeader(value="If-Match",required=false) String match){service.delete(p.getName(),id,match);return ResponseEntity.noContent().build();}
}
