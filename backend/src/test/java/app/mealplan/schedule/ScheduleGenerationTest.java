package app.mealplan.schedule;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ScheduleGenerationTest extends ScheduleShoppingSupport {

    @org.springframework.beans.factory.annotation.Autowired
    ScheduleGenerationService generation;

    @Test
    void explicitRetryOfOldFailedRecurringBatchClipsAlreadyCoveredDates() throws Exception {
        for (boolean covered : new boolean[] { false, true }) {
            clock.set(java.time.Instant.parse("2026-10-20T16:00:00Z"));
            String list = list(),
                source = source("2026-10-20");
            configure(list, source, "retry-late", 0, "").andExpect(status().isOk());
            setTemplate(source, "missing-template");
            clock.set(java.time.Instant.parse("2026-10-24T13:00:00Z"));
            generation.due();
            var repo = new OccurrenceRepository();
            String failed = db.read(c -> repo.latest(c, repo.active(c, list).id()).id());
            assertThat(db.<String>read(c -> repo.get(c, failed).status())).isEqualTo("failed");
            setTemplate(source, "three-week");
            clock.set(java.time.Instant.parse("2026-10-31T13:00:00Z"));
            if (covered) generation.due();
            int before = count(list);
            for (int i = 0; i < 2; i++) {
                mvc.perform(
                    post("/api/lists/" + list + "/schedule/retry")
                        .with(user("owner"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"occurrenceId\":\"" + failed + "\"}")
                ).andExpect(status().isOk());
                assertThat(db.<String>read(c -> repo.get(c, failed).status())).isEqualTo(
                    "completed"
                );
                if (i == 0) {
                    if (covered) assertThat(count(list)).isEqualTo(before);
                    else assertThat(count(list)).isGreaterThan(before);
                    before = count(list);
                } else assertThat(count(list)).isEqualTo(before);
            }
        }
    }

    private void setTemplate(String source, String template) {
        db.write(c -> {
            try (var q = c.prepareStatement("UPDATE user_schedules SET template_id=? WHERE id=?")) {
                q.setString(1, template);
                q.setString(2, source);
                q.executeUpdate();
            }
            return null;
        });
    }

    @Test
    void restartSkipsAnOldPendingRecurringBatchAfterSeveralMissedPeriods() throws Exception {
        String list = list(),
            source = source("2026-10-20");
        configure(list, source, "crash-recovery", 0, "").andExpect(status().isOk());
        var occurrences = new OccurrenceRepository();
        String old = db.write(c -> {
            var a = occurrences.active(c, list);
            var due = a.nextDueAt();
            var next = a.addition().nextAfter(due);
            var occurrence = occurrences.create(
                c,
                a,
                "recurring",
                due,
                a.addition().date(due),
                a.addition().date(next)
            );
            try (
                var q = c.prepareStatement(
                    "UPDATE schedule_attachments SET next_due_at=? WHERE id=?"
                )
            ) {
                q.setLong(1, next.toEpochMilli());
                q.setString(2, a.id());
                q.executeUpdate();
            }
            return occurrence.id();
        });
        clock.set(java.time.Instant.parse("2026-11-21T14:00:00Z"));
        generation.due();
        assertThat(db.<String>read(c -> occurrences.get(c, old).status())).isEqualTo("skipped");
        assertThat(
            db.<Integer>read(c -> {
                try (
                    var q = c.prepareStatement(
                        "SELECT count(*) FROM shopping_entries WHERE occurrence_id=?"
                    )
                ) {
                    q.setString(1, old);
                    try (var r = q.executeQuery()) {
                        return r.getInt(1);
                    }
                }
            })
        ).isZero();
        mvc.perform(get("/api/lists/" + list + "/schedule").with(user("owner")))
            .andExpect(jsonPath("$.latest.coverageStartDate").value("2026-11-21"))
            .andExpect(jsonPath("$.latest.status").value("completed"));
        int once = count(list);
        generation.due();
        assertThat(count(list)).isEqualTo(once);
    }

    @Test
    void recurrenceEditsPreserveCoverageAndViewerZonesOnlyClipDisplay() throws Exception {
        String list = list(),
            source = source("2026-10-20");
        configure(list, source, "original", 0, "").andExpect(status().isOk());
        clock.set(java.time.Instant.parse("2026-10-24T13:00:00Z"));
        generation.due();
        String monday =
            ",\"addition\":{\"unit\":\"week\",\"count\":1,\"weekday\":1,\"localTime\":\"09:00\",\"timeZone\":\"America/Toronto\",\"startDate\":\"2026-10-20\"}";
        configure(list, source, "monday", 1, monday)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attachment.lastCoveredEndDateExclusive").value("2026-10-31"));
        clock.set(java.time.Instant.parse("2026-10-26T13:00:00Z"));
        generation.due();
        mvc.perform(get("/api/lists/" + list + "/schedule").with(user("owner")))
            .andExpect(jsonPath("$.attachment.lastCoveredStartDate").value("2026-10-31"))
            .andExpect(jsonPath("$.attachment.lastCoveredEndDateExclusive").value("2026-11-02"));
        clock.set(java.time.Instant.parse("2026-10-20T10:30:00Z"));
        String b = list();
        configure(
            b,
            source,
            "long",
            0,
            ",\"addition\":{\"unit\":\"week\",\"count\":8,\"weekday\":6,\"localTime\":\"09:00\",\"timeZone\":\"America/Toronto\",\"startDate\":\"2026-10-20\"}"
        ).andExpect(status().isOk());
        var before = json(
            mvc.perform(get("/api/lists/" + b + "/schedule").with(user("owner"))).andReturn()
        );
        String previous = null;
        for (String zone : new String[] { "Pacific/Honolulu", "Pacific/Kiritimati" }) {
            var preview = json(
                mvc
                    .perform(
                        get("/api/lists/" + b + "/schedule/preview")
                            .with(user("owner"))
                            .param("timeZone", zone)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isPartial").value(true))
                    .andReturn()
            );
            String end = preview.get("displayedTo").asString();
            if (previous != null) org.assertj.core.api.Assertions.assertThat(end).isNotEqualTo(
                previous
            );
            previous = end;
            mvc.perform(
                get("/api/lists/" + b + "/schedule/meals")
                    .with(user("owner"))
                    .param("from", "2026-10-20")
                    .param("to", "2026-10-21")
                    .param("timeZone", zone)
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].cycleDay").value(1));
        }
        org.assertj.core.api.Assertions.assertThat(
            json(
                mvc.perform(get("/api/lists/" + b + "/schedule").with(user("owner"))).andReturn()
            ).toString()
        ).isEqualTo(before.toString());
    }

    @Test
    void failureAddsNothingAndRetryCompletesTheSameOccurrenceOnce() throws Exception {
        String list = list(),
            source = source("2026-10-20");
        db.write(c -> {
            try (
                var q = c.prepareStatement(
                    "UPDATE user_schedules SET template_id='missing-template' WHERE id=?"
                )
            ) {
                q.setString(1, source);
                q.executeUpdate();
            }
            return null;
        });
        var failed = json(
            configure(list, source, "failure", 0, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latest.status").value("failed"))
                .andExpect(jsonPath("$.attachment.lastCoveredEndDateExclusive").isEmpty())
                .andReturn()
        );
        org.assertj.core.api.Assertions.assertThat(count(list)).isZero();
        String id = failed.get("latest").get("id").asString();
        db.write(c -> {
            try (
                var q = c.prepareStatement(
                    "UPDATE user_schedules SET template_id='three-week' WHERE id=?"
                )
            ) {
                q.setString(1, source);
                q.executeUpdate();
            }
            return null;
        });
        for (int i = 0; i < 2; i++) mvc.perform(
            post("/api/lists/" + list + "/schedule/retry")
                .with(user("owner"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"occurrenceId\":\"" + id + "\"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latest.id").value(id))
            .andExpect(jsonPath("$.latest.status").value("completed"));
        org.assertj.core.api.Assertions.assertThat(count(list)).isPositive();
    }

    @Test
    void regularGenerationIsRepeatSafeAndCatchUpProcessesOnlyLatestPeriod() throws Exception {
        String list = list(),
            source = source("2026-10-20");
        configure(list, source, "recurring", 0, "").andExpect(status().isOk());
        clock.set(java.time.Instant.parse("2026-10-24T13:00:00Z"));
        generation.due();
        int once = count(list);
        generation.due();
        org.assertj.core.api.Assertions.assertThat(count(list)).isEqualTo(once);
        clock.set(java.time.Instant.parse("2026-11-21T14:00:00Z"));
        generation.due();
        mvc.perform(get("/api/lists/" + list + "/schedule").with(user("owner")))
            .andExpect(jsonPath("$.latest.coverageStartDate").value("2026-11-21"))
            .andExpect(jsonPath("$.attachment.lastCoveredEndDateExclusive").value("2026-11-28"));
        db.read(c -> {
            try (
                var q = c.prepareStatement(
                    "SELECT count(*) FROM schedule_occurrences o JOIN schedule_attachments a ON o.attachment_id=a.id WHERE a.list_id=?"
                )
            ) {
                q.setString(1, list);
                try (var r = q.executeQuery()) {
                    org.assertj.core.api.Assertions.assertThat(r.getInt(1)).isEqualTo(3);
                }
            }
            return null;
        });
    }

    @Test
    void importIsImmediateAndIdempotentWithLiveSourceAndCoverage() throws Exception {
        String list = list(),
            source = source("2026-10-20");
        configure(list, source, "first", 0, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latest.kind").value("initial"))
            .andExpect(jsonPath("$.latest.status").value("completed"))
            .andExpect(jsonPath("$.attachment.lastCoveredEndDateExclusive").value("2026-10-24"));
        int original = count(list);
        assertThat(original).isPositive();
        configure(list, source, "first", 0, "").andExpect(status().isOk());
        assertThat(count(list)).isEqualTo(original);
        mvc.perform(
            get("/api/lists/" + list + "/schedule/meals")
                .with(user("other"))
                .param("from", "2026-10-20")
                .param("to", "2026-10-21")
                .param("timeZone", "UTC")
        ).andExpect(status().isNotFound());
        mvc.perform(
            patch("/api/user-schedules/" + source)
                .with(user("owner"))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType("application/json")
                .content("{\"anchorDate\":\"2026-10-21\"}")
        ).andExpect(status().isOk());
        mvc.perform(get("/api/lists/" + list + "/schedule").with(user("owner")))
            .andExpect(jsonPath("$.anchorDate").value("2026-10-21"))
            .andExpect(jsonPath("$.attachment.lastCoveredEndDateExclusive").isEmpty());
        assertThat(count(list)).isEqualTo(original);
    }

    @Test
    void preAnchorAndSaturdayMorningInitialBatchesCompleteEmpty() throws Exception {
        String list = list(),
            source = source("2027-01-01");
        configure(list, source, "future", 0, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latest.status").value("completed"));
        assertThat(count(list)).isZero();
        clock.set(java.time.Instant.parse("2026-10-24T11:00:00Z"));
        String second = list(),
            active = source("2026-10-20");
        configure(second, active, "saturday", 0, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latest.coverageStartDate").value("2026-10-24"))
            .andExpect(jsonPath("$.latest.coverageEndDateExclusive").value("2026-10-24"));
        assertThat(count(second)).isZero();
    }
}
