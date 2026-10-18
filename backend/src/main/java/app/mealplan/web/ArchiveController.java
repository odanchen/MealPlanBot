package app.mealplan.web;

import app.mealplan.shopping.ArchiveService;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ArchiveController {

    private final ArchiveService service;

    public ArchiveController(ArchiveService service) {
        this.service = service;
    }

    @GetMapping("/api/lists/{id}/archive")
    public Object archive(Principal p, @PathVariable String id) {
        return Map.of("entries", service.entries(p.getName(), id));
    }

    @DeleteMapping("/api/lists/{id}/archive")
    public ResponseEntity<Void> clear(Principal p, @PathVariable String id) {
        service.clear(p.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
