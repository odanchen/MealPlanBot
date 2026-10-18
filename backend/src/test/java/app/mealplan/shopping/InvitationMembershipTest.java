package app.mealplan.shopping;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InvitationMembershipTest extends SharingHttpSupport {

    @Test
    void explicitMultiUseAcceptanceAndImmediateLeavePrivacy() throws Exception {
        String list = list(),
            base = "/api/lists/" + list;
        var invitation = invite(list);
        String token = token(invitation);
        mvc.perform(get("/api/invitations/accept").param("token", token)).andExpect(
            status().isUnauthorized()
        );
        mvc.perform(
            post("/api/invitations/accept")
                .with(csrf())
                .contentType("application/json")
                .content("{\"token\":\"" + token + "\"}")
        ).andExpect(status().isUnauthorized());
        mvc.perform(get(base).with(user("guest"))).andExpect(status().isNotFound());
        accept("guest", token).andExpect(status().isOk());
        accept("guest", token).andExpect(status().isOk());
        accept("second", token).andExpect(status().isOk());
        String item = entry(list, "guest", "Apples");
        mvc.perform(get(base + "/invitations").with(user("guest"))).andExpect(
            status().isForbidden()
        );
        mvc.perform(
            delete(base).with(user("guest")).with(csrf()).header("If-Match", "\"0\"")
        ).andExpect(status().isForbidden());
        mvc.perform(
            delete(base + "/membership")
                .with(user("owner"))
                .with(csrf())
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ownerCannotLeave"));
        mvc.perform(
            delete(base + "/membership")
                .with(user("guest"))
                .with(csrf())
        ).andExpect(status().isNoContent());
        mvc.perform(get(base).with(user("guest"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/lists").with(user("guest"))).andExpect(
            jsonPath("$.lists[?(@.id == '" + list + "')]").isEmpty()
        );
        mvc.perform(
            patch(base + "/entries/" + item)
                .with(user("guest"))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType("application/json")
                .content("{\"bought\":true}")
        ).andExpect(status().isNotFound());
        mvc.perform(get(base + "/entries").with(user("owner"))).andExpect(
            jsonPath("$.entries[0].id").value(item)
        );
        mvc.perform(get(base + "/invitations").with(user("owner")))
            .andExpect(status().isOk())
            .andExpect(
                content().string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(token))
                )
            );
        db.read(c -> {
            try (
                var q = c.prepareStatement(
                    "SELECT token_hash,expires_at-created_at FROM invitations WHERE list_id=?"
                )
            ) {
                q.setString(1, list);
                try (var r = q.executeQuery()) {
                    assertThat(r.next()).isTrue();
                    assertThat(r.getString(1)).isNotEqualTo(token);
                    assertThat(r.getLong(2)).isEqualTo(Duration.ofHours(72).toMillis());
                }
            }
            return null;
        });
    }

    @Test
    void exactExpiryWithdrawalAndDeletedListTokensCannotGrantAccess() throws Exception {
        String list = list();
        var first = invite(list);
        String token = token(first);
        clock.advance(Duration.ofHours(72).minusMillis(1));
        accept("just-before", token).andExpect(status().isOk());
        clock.advance(Duration.ofMillis(1));
        accept("at-expiry", token).andExpect(status().isNotFound());
        var second = invite(list);
        mvc.perform(
            delete(
                "/api/lists/" +
                    list +
                    "/invitations/" +
                    second.get("invitation").get("id").asString()
            )
                .with(user("owner"))
                .with(csrf())
        ).andExpect(status().isNoContent());
        accept("withdrawn", token(second)).andExpect(status().isNotFound());
        var third = invite(list);
        mvc.perform(
            delete("/api/lists/" + list)
                .with(user("owner"))
                .with(csrf())
                .header("If-Match", "\"1\"")
        ).andExpect(status().isConflict());
        mvc.perform(
            delete("/api/lists/" + list)
                .with(user("owner"))
                .with(csrf())
                .header("If-Match", "\"0\"")
        ).andExpect(status().isNoContent());
        accept("deleted", token(third)).andExpect(status().isNotFound());
    }

    @Test
    void archiveClearingIsMemberOnlyAndPreservesActiveItems() throws Exception {
        String list = list(),
            active = entry(list, "owner", "Active"),
            archived = entry(list, "owner", "Old");
        db.write(c -> {
            try (
                var q = c.prepareStatement("UPDATE shopping_entries SET archived_at=? WHERE id=?")
            ) {
                q.setLong(1, clock.instant().toEpochMilli());
                q.setString(2, archived);
                q.executeUpdate();
            }
            return null;
        });
        mvc.perform(get("/api/lists/" + list + "/archive").with(user("unrelated"))).andExpect(
            status().isNotFound()
        );
        accept("archive-member", token(invite(list))).andExpect(status().isOk());
        mvc.perform(get("/api/lists/" + list + "/archive").with(user("archive-member")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].id").value(archived));
        mvc.perform(
            delete("/api/lists/" + list + "/archive")
                .with(user("unrelated"))
                .with(csrf())
        ).andExpect(status().isNotFound());
        mvc.perform(
            delete("/api/lists/" + list + "/entries/" + archived)
                .with(user("archive-member"))
                .with(csrf())
                .header("If-Match", "\"0\"")
        ).andExpect(status().isNoContent());
        String old = entry(list, "owner", "Another old");
        db.write(c -> {
            try (
                var q = c.prepareStatement("UPDATE shopping_entries SET archived_at=1 WHERE id=?")
            ) {
                q.setString(1, old);
                q.executeUpdate();
            }
            return null;
        });
        mvc.perform(
            delete("/api/lists/" + list + "/archive")
                .with(user("archive-member"))
                .with(csrf())
        ).andExpect(status().isNoContent());
        mvc.perform(get("/api/lists/" + list + "/entries").with(user("owner")))
            .andExpect(jsonPath("$.entries.length()").value(1))
            .andExpect(jsonPath("$.entries[0].id").value(active));
    }
}
