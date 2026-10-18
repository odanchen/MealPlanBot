package app.mealplan.shopping;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import app.mealplan.MealPlanApplication;
import app.mealplan.identity.IdentityService;
import app.mealplan.support.SqliteTestSupport;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;

@SpringBootTest(
    classes = MealPlanApplication.class,
    properties = { "mealplan.deployment-target=test", "mealplan.fixture-auth=false" }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PrivateListTest {

    static final Path DIR;

    static {
        try {
            DIR = Files.createTempDirectory("shopping-contract-");
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

    String create(String subject, String key) throws Exception {
        return id(
            mvc
                .perform(
                    post("/api/lists")
                        .with(user(subject))
                        .with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"name\":\"Groceries\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schedule").isEmpty())
                .andReturn()
        );
    }

    String id(MvcResult r) throws Exception {
        return new tools.jackson.databind.json.JsonMapper()
            .readTree(r.getResponse().getContentAsString())
            .get("id")
            .asString();
    }

    MockHttpServletRequestBuilder add(String list, String key, String body) {
        return post("/api/lists/" + list + "/entries")
            .with(user("alice"))
            .with(csrf())
            .header("Idempotency-Key", key)
            .contentType("application/json")
            .content(body);
    }

    @Test
    void listCreationValidationAndAllResourceBoundaries() throws Exception {
        mvc.perform(
            post("/api/lists")
                .with(user("alice"))
                .with(csrf())
                .header("Idempotency-Key", "blank-list")
                .contentType("application/json")
                .content("{\"name\":\" \"}")
        ).andExpect(status().isUnprocessableContent());
        mvc.perform(
            post("/api/lists")
                .with(user("alice"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"List\"}")
        ).andExpect(status().isUnprocessableContent());
        String list = create("alice", java.util.UUID.randomUUID().toString()),
            path = "/api/lists/" + list;
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path + "/entries")).andExpect(status().isUnauthorized());
        mvc.perform(get(path + "/entries").with(user("bob"))).andExpect(status().isNotFound());
        String entry = id(
            mvc
                .perform(add(list, "boundary-item", "{\"name\":\"Bread\"}"))
                .andExpect(status().isCreated())
                .andReturn()
        );
        mvc.perform(
            patch(path + "/entries/" + entry)
                .with(user("bob"))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType("application/json")
                .content("{\"bought\":true}")
        ).andExpect(status().isNotFound());
        mvc.perform(
            patch(path + "/entries/" + entry)
                .with(user("alice"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"bought\":true}")
        ).andExpect(status().isUnprocessableContent());
        mvc.perform(
            get(path + "/entries")
                .with(user("alice"))
                .param("sort", "invalid")
        ).andExpect(status().isUnprocessableContent());
    }

    @Test
    void privateDurableListsAndIndependentEntries() throws Exception {
        mvc.perform(get("/api/lists")).andExpect(status().isUnauthorized());
        String list = create("alice", "private-list" + java.util.UUID.randomUUID()),
            path = "/api/lists/" + list;
        mvc.perform(get(path).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/lists").with(user("bob"))).andExpect(jsonPath("$.lists").isEmpty());
        mvc.perform(
            post(path + "/entries")
                .with(user("bob"))
                .with(csrf())
                .header("Idempotency-Key", "bob")
                .contentType("application/json")
                .content("{\"name\":\"Secret\"}")
        ).andExpect(status().isNotFound());
        String body = "{\"name\":\"Tomatoes\",\"quantity\":\"1.50\",\"unit\":\"kg\"}";
        String entry = id(
            mvc
                .perform(add(list, "first", body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value("1.5"))
                .andExpect(jsonPath("$.boughtAt").isEmpty())
                .andReturn()
        );
        mvc.perform(
            patch(path + "/entries/" + entry)
                .with(user("alice"))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType("application/json")
                .content("{\"bought\":true}")
        ).andExpect(status().isOk());
        mvc.perform(add(list, "second", body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.boughtAt").isEmpty());
        for (String invalid : new String[] {
            "{\"name\":\" \"}",
            "{\"name\":\"Rice\",\"quantity\":\"0\",\"unit\":\"kg\"}",
            "{\"name\":\"Rice\",\"quantity\":\"1\"}",
            "{\"name\":\"Rice\",\"unit\":\"kg\"}",
        })
            mvc.perform(add(list, java.util.UUID.randomUUID().toString(), invalid)).andExpect(
                status().isUnprocessableContent()
            );
        identities.resolve("https://accounts.google.com", "alice", "Alice", "new@example.test");
        mvc.perform(get(path + "/entries").with(user("alice")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries.length()").value(2))
            .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(
            delete(path + "/entries/" + entry)
                .with(user("bob"))
                .with(csrf())
                .header("If-Match", "\"1\"")
        ).andExpect(status().isNotFound());
        mvc.perform(
            patch(path + "/entries/" + entry)
                .with(user("alice"))
                .header("If-Match", "\"1\"")
                .contentType("application/json")
                .content("{\"bought\":false}")
        ).andExpect(status().isForbidden());
    }
}
