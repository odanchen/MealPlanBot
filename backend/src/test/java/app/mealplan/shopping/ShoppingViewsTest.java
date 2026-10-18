package app.mealplan.shopping;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import app.mealplan.schedule.ScheduleShoppingSupport;
import java.util.*;
import org.junit.jupiter.api.Test;

class ShoppingViewsTest extends ScheduleShoppingSupport {

    @Test
    void eachViewPreservesAllStatesAndUsesStableTies() throws Exception {
        String list = list();
        var ids = new ArrayList<String>();
        for (String name : List.of("Zucchini", "apples", "Bananas")) {
            ids.add(
                json(
                    mvc
                        .perform(
                            post("/api/lists/" + list + "/entries")
                                .with(user("owner"))
                                .with(csrf())
                                .header("Idempotency-Key", UUID.randomUUID())
                                .contentType("application/json")
                                .content(
                                    "{\"name\":\"" + name + "\",\"quantity\":\"1\",\"unit\":\"kg\"}"
                                )
                        )
                        .andExpect(status().isCreated())
                        .andReturn()
                )
                    .get("id")
                    .asString()
            );
            clock.advance(java.time.Duration.ofSeconds(1));
        }
        mvc.perform(
            patch("/api/lists/" + list + "/entries/" + ids.get(1))
                .with(user("owner"))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType("application/json")
                .content("{\"bought\":true}")
        ).andExpect(status().isOk());
        for (String sort : List.of("alphabetical", "chronological"))
            for (boolean group : List.of(false, true)) {
                var rows = json(
                    mvc
                        .perform(
                            get("/api/lists/" + list + "/entries")
                                .with(user("owner"))
                                .param("sort", sort)
                                .param("groupBought", Boolean.toString(group))
                        )
                        .andExpect(status().isOk())
                        .andReturn()
                ).get("entries");
                var names = new ArrayList<String>();
                var returned = new ArrayList<String>();
                for (var row : rows) {
                    names.add(row.get("name").asString());
                    returned.add(row.get("id").asString());
                    assertThat(row.get("quantity").asString()).isEqualTo("1");
                    if (row.get("id").asString().equals(ids.get(1))) assertThat(
                        row.get("boughtAt").isNull()
                    ).isFalse();
                }
                assertThat(returned).containsExactlyInAnyOrderElementsOf(ids);
                assertThat(names).containsExactlyElementsOf(
                    sort.equals("alphabetical")
                        ? group
                            ? List.of("Bananas", "Zucchini", "apples")
                            : List.of("apples", "Bananas", "Zucchini")
                        : group
                          ? List.of("Zucchini", "Bananas", "apples")
                          : List.of("Zucchini", "apples", "Bananas")
                );
            }
        var tied = new ArrayList<String>();
        for (int i = 0; i < 2; i++) tied.add(
            json(
                mvc
                    .perform(
                        post("/api/lists/" + list + "/entries")
                            .with(user("owner"))
                            .with(csrf())
                            .header("Idempotency-Key", UUID.randomUUID())
                            .contentType("application/json")
                            .content("{\"name\":\"Same\"}")
                    )
                    .andExpect(status().isCreated())
                    .andReturn()
            )
                .get("id")
                .asString()
        );
        tied.sort(Comparator.naturalOrder());
        for (String sort : List.of("alphabetical", "chronological")) {
            var rows = json(
                mvc
                    .perform(
                        get("/api/lists/" + list + "/entries")
                            .with(user("owner"))
                            .param("sort", sort)
                            .param("groupBought", "false")
                    )
                    .andExpect(status().isOk())
                    .andReturn()
            ).get("entries");
            var actual = new ArrayList<String>();
            for (var row : rows)
                if (row.get("name").asString().equals("Same")) actual.add(row.get("id").asString());
            assertThat(actual).containsExactlyElementsOf(tied);
        }
    }
}
