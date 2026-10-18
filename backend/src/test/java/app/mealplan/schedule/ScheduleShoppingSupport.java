package app.mealplan.schedule;

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
    properties = {
        "mealplan.deployment-target=test",
        "mealplan.fixture-auth=false",
        "mealplan.scheduling-enabled=false",
    }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(ScheduleShoppingSupport.Time.class)
public abstract class ScheduleShoppingSupport {

    @TestConfiguration(proxyBeanMethods = false)
    static class Time {

        @Bean
        @Primary
        MutableClock scheduleClock() {
            return new MutableClock(Instant.parse("2026-10-20T16:00:00Z"));
        }
    }

    static final Path DIR;

    static {
        try {
            DIR = Files.createTempDirectory("schedule-shopping-");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("mealplan.database-path", () -> DIR.resolve("app.sqlite").toString());
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected IdentityService identities;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected SqliteDatabase db;

    @BeforeEach
    void resetTime() {
        clock.set(Instant.parse("2026-10-20T16:00:00Z"));
    }

    protected RequestPostProcessor user(String subject) {
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

    protected tools.jackson.databind.JsonNode json(MvcResult r) throws Exception {
        return new tools.jackson.databind.json.JsonMapper().readTree(
            r.getResponse().getContentAsString()
        );
    }

    protected String list() throws Exception {
        return json(
            mvc
                .perform(
                    post("/api/lists")
                        .with(user("owner"))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"name\":\"Auto household\"}")
                )
                .andExpect(status().isCreated())
                .andReturn()
        )
            .get("id")
            .asString();
    }

    protected String source(String date) throws Exception {
        return json(
            mvc
                .perform(
                    post("/api/user-schedules")
                        .with(user("owner"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"templateId\":\"three-week\",\"anchorDate\":\"" + date + "\"}")
                )
                .andExpect(status().isCreated())
                .andReturn()
        )
            .get("id")
            .asString();
    }

    protected ResultActions configure(
        String list,
        String source,
        String key,
        long version,
        String extra
    ) throws Exception {
        return mvc.perform(
            put("/api/lists/" + list + "/schedule")
                .with(user("owner"))
                .with(csrf())
                .header("Idempotency-Key", key)
                .header("If-Match", "\"" + version + "\"")
                .contentType("application/json")
                .content("{\"userScheduleId\":\"" + source + "\"" + extra + "}")
        );
    }

    protected int count(String list) throws Exception {
        return json(
            mvc
                .perform(get("/api/lists/" + list + "/entries").with(user("owner")))
                .andExpect(status().isOk())
                .andReturn()
        )
            .get("entries")
            .size();
    }
}
