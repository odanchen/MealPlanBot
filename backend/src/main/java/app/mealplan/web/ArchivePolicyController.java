package app.mealplan.web;

import app.mealplan.shopping.*;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ArchivePolicyController {

    private final ItemAgePolicy service;

    public ArchivePolicyController(ItemAgePolicy service) {
        this.service = service;
    }

    public record Policy(ItemAgePolicy.Limit ageLimit) {}

    @PutMapping("/api/lists/{id}/archive-policy")
    public Object update(
        Principal p,
        @PathVariable String id,
        @RequestBody Policy input,
        @RequestHeader(value = "If-Match", required = false) String match
    ) {
        return service.update(p.getName(), id, input.ageLimit(), match);
    }
}
