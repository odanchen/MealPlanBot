package app.mealplan.acceptance;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import app.mealplan.schedule.ScheduleShoppingSupport;
import org.junit.jupiter.api.Test;

class AccessBoundaryTest extends ScheduleShoppingSupport {

    @Test
    void privateBoundariesAndDiagnosticsNeverExposeListOrInvitation() throws Exception {
        String list = list();
        mvc.perform(get("/api/lists/" + list))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/lists/" + list).with(user("foreign"))).andExpect(
            status().isNotFound()
        );
        mvc.perform(post("/api/lists/" + list + "/invitations").with(user("owner"))).andExpect(
            status().isForbidden()
        );
        var invitation = json(
            mvc
                .perform(
                    post("/api/lists/" + list + "/invitations")
                        .with(user("owner"))
                        .with(csrf())
                )
                .andExpect(status().isCreated())
                .andReturn()
        );
        String link = invitation.get("link").asString();
        assertThat(link).contains("/join#token=");
        String diagnostics = mvc
            .perform(get("/health"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(diagnostics).doesNotContain(list, link, "token", "sqlite");
        for (String route : new String[] {
            "/api/recipes/1",
            "/api/week",
            "/api/today",
            "/api/schedule",
            "/__test/advance",
        })
            mvc.perform(get(route).with(user("owner"))).andExpect(status().isNotFound());
        mvc.perform(
            delete("/api/lists/" + list)
                .with(user("owner"))
                .with(csrf())
                .header("If-Match", "\"0\"")
        ).andExpect(status().isNoContent());
        String token = link.substring(link.indexOf("token=") + 6);
        mvc.perform(
            post("/api/invitations/accept")
                .with(user("foreign"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"token\":\"" + token + "\"}")
        ).andExpect(status().isNotFound());
    }
}
