package app.mealplan.web;

import app.mealplan.content.*;
import app.mealplan.schedule.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public final class UserScheduleController {

    private final UserScheduleDeletionService deletion;
    private final ContentLoader content;
    private final UserScheduleService service;

    public UserScheduleController(
        ContentLoader content,
        UserScheduleService service,
        UserScheduleDeletionService deletion
    ) {
        this.content = content;
        this.service = service;
        this.deletion = deletion;
    }

    @GetMapping("/api/schedule-templates")
    public Object templates() {
        var s = content.snapshot();
        return Map.of("revision", s.revision(), "templates", s.templates().values());
    }

    @GetMapping("/api/schedule-templates/{id}")
    public Object template(@PathVariable String id) {
        var s = content.snapshot();
        var t = s.templates().get(id);
        if (t == null) throw ApiErrors.notFound();
        return Map.of("revision", s.revision(), "template", t);
    }

    @DeleteMapping("/api/user-schedules/{id}")
    public ResponseEntity<Void> delete(
        Principal p,
        @PathVariable String id,
        @RequestHeader(value = "If-Match", required = false) String match
    ) {
        deletion.delete(p.getName(), id, match);
        return ResponseEntity.noContent().build();
    }

    public record Signup(String templateId, LocalDate anchorDate) {}

    public record Anchor(LocalDate anchorDate) {}

    @PostMapping("/api/user-schedules")
    public ResponseEntity<ConfiguredUserSchedule> create(Principal p, @RequestBody Signup input) {
        var result = service.create(p.getName(), input.templateId(), input.anchorDate());
        return ResponseEntity.created(java.net.URI.create("/api/user-schedules/" + result.id()))
            .eTag(Long.toString(result.version()))
            .body(result);
    }

    @GetMapping("/api/user-schedules")
    public Object list(Principal p) {
        return Map.of("schedules", service.list(p.getName()));
    }

    @GetMapping("/api/user-schedules/{id}")
    public ResponseEntity<?> detail(Principal p, @PathVariable String id) {
        var config = service.get(p.getName(), id);
        return ResponseEntity.ok()
            .eTag(Long.toString(config.version()))
            .body(Map.of("schedule", config, "linkedLists", deletion.linked(p.getName(), id)));
    }

    @PatchMapping("/api/user-schedules/{id}")
    public ResponseEntity<?> update(
        Principal p,
        @PathVariable String id,
        @RequestBody Anchor input,
        @RequestHeader(value = "If-Match", required = false) String match
    ) {
        var result = service.update(p.getName(), id, input.anchorDate(), match);
        return ResponseEntity.ok().eTag(Long.toString(result.version())).body(result);
    }

    @GetMapping("/api/user-schedules/{id}/meals")
    public Object meals(
        Principal p,
        @PathVariable String id,
        @RequestParam LocalDate from,
        @RequestParam LocalDate to,
        @RequestParam String timeZone
    ) {
        return service.meals(p.getName(), id, from, to, timeZone);
    }
}
