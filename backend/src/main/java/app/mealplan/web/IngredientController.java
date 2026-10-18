package app.mealplan.web;

import app.mealplan.content.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public final class IngredientController {

    private final ContentLoader content;

    public IngredientController(ContentLoader content) {
        this.content = content;
    }

    @GetMapping("/api/ingredients")
    public Object list() {
        var s = content.snapshot();
        return Map.of(
            "revision",
            s.revision(),
            "categories",
            s.categories(),
            "ingredients",
            s.ingredients(),
            "units",
            app.mealplan.domain.measurement.UnitRegistry.definitions()
        );
    }
}
