package app.mealplan.web;

import app.mealplan.schedule.*;
import java.security.Principal;
import java.time.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lists/{id}/schedule")
public class ListScheduleController {

    private final ScheduleAttachmentService service;
    private final ScheduleGenerationService generation;
    private final ScheduleRetryService retries;

    public ListScheduleController(
        ScheduleAttachmentService service,
        ScheduleGenerationService generation,
        ScheduleRetryService retries
    ) {
        this.service = service;
        this.generation = generation;
        this.retries = retries;
    }

    @GetMapping
    public Object state(Principal p, @PathVariable String id) {
        return service.state(p.getName(), id);
    }

    @PutMapping
    public Object configure(
        Principal p,
        @PathVariable String id,
        @RequestBody ScheduleAttachmentService.Configuration input,
        @RequestHeader(value = "If-Match", required = false) String match,
        @RequestHeader(value = "Idempotency-Key", required = false) String key
    ) {
        var result = service.configure(p.getName(), id, input, match, key);
        generation.initial(result.attachmentId());
        return service.state(p.getName(), id);
    }

    @DeleteMapping
    public ResponseEntity<Void> detach(
        Principal p,
        @PathVariable String id,
        @RequestHeader(value = "If-Match", required = false) String match
    ) {
        service.detach(p.getName(), id, match);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/meals")
    public Object meals(
        Principal p,
        @PathVariable String id,
        @RequestParam LocalDate from,
        @RequestParam LocalDate to,
        @RequestParam String timeZone
    ) {
        return service.meals(p.getName(), id, from, to, timeZone);
    }

    @GetMapping("/preview")
    public Object preview(Principal p, @PathVariable String id, @RequestParam String timeZone) {
        return service.preview(p.getName(), id, timeZone);
    }

    public record Retry(String occurrenceId) {}

    @PostMapping("/retry")
    public Object retry(Principal p, @PathVariable String id, @RequestBody Retry input) {
        retries.retry(p.getName(), id, input.occurrenceId());
        return service.state(p.getName(), id);
    }
}
