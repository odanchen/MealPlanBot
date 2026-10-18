package app.mealplan.shopping;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import app.mealplan.MealPlanApplication;
import app.mealplan.identity.IdentityService;
import app.mealplan.persistence.SqliteDatabase;
import app.mealplan.support.*;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;

@SpringBootTest(
    classes = MealPlanApplication.class,
    properties = { "mealplan.deployment-target=test", "mealplan.fixture-auth=false" }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(SharingHttpSupport.Time.class)
abstract class SharingHttpSupport {

    @TestConfiguration(proxyBeanMethods = false)
    static class Time {

        @Bean
        @Primary
        MutableClock sharingClock() {
            return new MutableClock(Instant.parse("2026-10-18T12:00:00Z"));
        }
    }

    static final Path DIR;

    static {
        try {
            DIR = Files.createTempDirectory("sharing-contract-");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("mealplan.database-path", () -> DIR.resolve("app.sqlite").toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityService identities;

    @Autowired
    MutableClock clock;

    @Autowired
    SqliteDatabase db;

    @BeforeEach
    void resetTime() {
        clock.set(Instant.parse("2026-10-18T12:00:00Z"));
    }

    RequestPostProcessor user(String subject) {
        return authentication(
            SqliteTestSupport.principal(
                identities
                    .resolve(
                        "https://accounts.google.com",
                        subject,
                        subject,
                        subject + "@example.test"
                    )
                    .id()
            )
        );
    }

    tools.jackson.databind.JsonNode json(MvcResult r) throws Exception {
        return new tools.jackson.databind.json.JsonMapper().readTree(
            r.getResponse().getContentAsString()
        );
    }

    String list() throws Exception {
        return json(
            mvc
                .perform(
                    post("/api/lists")
                        .with(user("owner"))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"name\":\"Household\"}")
                )
                .andExpect(status().isCreated())
                .andReturn()
        )
            .get("id")
            .asString();
    }

    tools.jackson.databind.JsonNode invite(String list) throws Exception {
        return json(
            mvc
                .perform(
                    post("/api/lists/" + list + "/invitations")
                        .with(user("owner"))
                        .with(csrf())
                )
                .andExpect(status().isCreated())
                .andReturn()
        );
    }

    String token(tools.jackson.databind.JsonNode invite) {
        return invite.get("link").asString().split("token=")[1];
    }

    ResultActions accept(String subject, String token) throws Exception {
        return mvc.perform(
            post("/api/invitations/accept")
                .with(user(subject))
                .with(csrf())
                .contentType("application/json")
                .content("{\"token\":\"" + token + "\"}")
        );
    }

    String entry(String list, String subject, String name) throws Exception {
        return json(
            mvc
                .perform(
                    post("/api/lists/" + list + "/entries")
                        .with(user(subject))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\"}")
                )
                .andExpect(status().isCreated())
                .andReturn()
        )
            .get("id")
            .asString();
    }
}
