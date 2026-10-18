package app.mealplan.web;

import app.mealplan.content.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public final class GlossaryController {

    private final ContentLoader content;

    public GlossaryController(ContentLoader content) {
        this.content = content;
    }

    @GetMapping("/api/glossary")
    public Object list() {
        var s = content.snapshot();
        return Map.of("revision", s.revision(), "terms", s.terms());
    }
}
