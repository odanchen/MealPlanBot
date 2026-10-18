package app.mealplan.web;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import app.mealplan.schedule.ScheduleShoppingSupport;
import org.junit.jupiter.api.Test;

class MeasurementDisplayContractTest extends ScheduleShoppingSupport {

    @Test
    void selectedDisplayPreservesCanonicalRecipeAndRejectsUnknownSystems() throws Exception {
        var metric = json(
            mvc
                .perform(
                    get("/api/recipes/red-wine-beef-stew-with-mashed-potatoes").param(
                        "measurementSystem",
                        "metric"
                    )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurementSystem").value("metric"))
                .andReturn()
        );
        var us = json(
            mvc
                .perform(
                    get("/api/recipes/red-wine-beef-stew-with-mashed-potatoes").param(
                        "measurementSystem",
                        "us"
                    )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurementSystem").value("us"))
                .andExpect(jsonPath("$.ingredientDisplay[0].displayUnit").value("lb"))
                .andReturn()
        );
        assertThat(us.get("recipe").get("ingredients").toString()).isEqualTo(
            metric.get("recipe").get("ingredients").toString()
        );
        mvc.perform(get("/api/recipes/simple-rice").param("measurementSystem", "guess")).andExpect(
            status().isUnprocessableContent()
        );
    }
}
