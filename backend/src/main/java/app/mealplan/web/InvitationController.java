package app.mealplan.web;

import app.mealplan.shopping.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class InvitationController {

    private final InvitationService service;

    public InvitationController(InvitationService service) {
        this.service = service;
    }

    public record Accept(String token) {}

    @PostMapping("/api/lists/{id}/invitations")
    public ResponseEntity<?> create(Principal p, @PathVariable String id) {
        return ResponseEntity.status(201).body(service.create(p.getName(), id));
    }

    @GetMapping("/api/lists/{id}/invitations")
    public Object list(Principal p, @PathVariable String id) {
        return Map.of("invitations", service.list(p.getName(), id));
    }

    @DeleteMapping("/api/lists/{id}/invitations/{invitationId}")
    public ResponseEntity<Void> withdraw(
        Principal p,
        @PathVariable String id,
        @PathVariable String invitationId
    ) {
        service.withdraw(p.getName(), id, invitationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/invitations/accept")
    public Object accept(Principal p, @RequestBody Accept input) {
        return service.accept(p.getName(), input.token());
    }
}
