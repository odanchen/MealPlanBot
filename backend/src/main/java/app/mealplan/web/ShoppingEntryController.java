package app.mealplan.web;

import app.mealplan.shopping.*;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lists/{id}/entries")
public class ShoppingEntryController {

    private final ShoppingEntryService service;

    public ShoppingEntryController(ShoppingEntryService service) {
        this.service = service;
    }

    public record Bought(Boolean bought) {}

    @GetMapping
    public Object entries(
        Principal p,
        @PathVariable String id,
        @RequestParam(defaultValue = "active") String state,
        @RequestParam(defaultValue = "alphabetical") String sort,
        @RequestParam(defaultValue = "true") boolean groupBought
    ) {
        return Map.of("entries", service.entries(p.getName(), id, state, sort, groupBought));
    }

    @PostMapping
    public ResponseEntity<ShoppingEntry> add(
        Principal p,
        @PathVariable String id,
        @RequestBody ShoppingEntryService.Addition input,
        @RequestHeader(value = "Idempotency-Key", required = false) String key
    ) {
        var result = service.add(p.getName(), id, input, key);
        return ResponseEntity.created(URI.create("/api/lists/" + id + "/entries/" + result.id()))
            .eTag(Long.toString(result.version()))
            .body(result);
    }

    @PatchMapping("/{entryId}")
    public ResponseEntity<ShoppingEntry> bought(
        Principal p,
        @PathVariable String id,
        @PathVariable String entryId,
        @RequestBody Bought input,
        @RequestHeader(value = "If-Match", required = false) String match
    ) {
        var result = service.bought(p.getName(), id, entryId, input.bought(), match);
        return ResponseEntity.ok().eTag(Long.toString(result.version())).body(result);
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> remove(
        Principal p,
        @PathVariable String id,
        @PathVariable String entryId,
        @RequestHeader(value = "If-Match", required = false) String match
    ) {
        service.remove(p.getName(), id, entryId, match);
        return ResponseEntity.noContent().build();
    }
}
