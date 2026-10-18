package app.mealplan.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ApiErrorsTest {

    @RestController
    static class Failures {

        @GetMapping("/failure")
        String failure() {
            throw new IllegalStateException("secret-token-and-private-payload");
        }

        @GetMapping("/missing")
        String missing() {
            throw ApiErrors.notFound();
        }

        @GetMapping("/owner")
        String owner() {
            throw ApiErrors.forbidden();
        }

        @GetMapping("/version")
        String version() {
            VersionPreconditions.check("\"1\"", 2);
            return "";
        }
    }

    @Test
    void usesContractStatusesAndSafeDiagnostics() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Failures())
            .setControllerAdvice(new ApiErrors())
            .build();
        mvc.perform(get("/failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                content().string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-token"))
                )
            );
        mvc.perform(get("/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/owner")).andExpect(status().isForbidden());
        mvc.perform(get("/version"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.currentVersion").value(2));
    }
}
