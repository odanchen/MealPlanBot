package app.mealplan.schedule;

import app.mealplan.content.*;
import app.mealplan.domain.schedule.*;
import app.mealplan.persistence.*;
import app.mealplan.shopping.*;
import app.mealplan.web.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ScheduleAttachmentService {

    public record Configuration(
        String userScheduleId,
        AdditionRecurrence addition,
        Boolean enabled
    ) {}

    public record ImportResult(String attachmentId) {}

    public record State(
        ScheduleAttachment attachment,
        ScheduleOccurrence latest,
        String templateId,
        LocalDate anchorDate,
        Long sourceVersion
    ) {}

    private final SqliteDatabase db;
    private final ListAccessPolicy access;
    private final ShoppingRepository shopping;
    private final OccurrenceRepository occurrences;
    private final IdempotentOperations operations;
    private final Clock clock;
    private final ContentLoader content;

    public ScheduleAttachmentService(
        SqliteDatabase db,
        MigrationRunner migrations,
        ListAccessPolicy access,
        ShoppingRepository shopping,
        OccurrenceRepository occurrences,
        IdempotentOperations operations,
        Clock clock,
        ContentLoader content
    ) {
        this.db = db;
        this.access = access;
        this.shopping = shopping;
        this.occurrences = occurrences;
        this.operations = operations;
        this.clock = clock;
        this.content = content;
    }

    public ImportResult configure(
        String user,
        String list,
        Configuration input,
        String match,
        String key
    ) {
        return db.write(c -> {
            access.owner(c, user, list);
            return operations.execute(
                c,
                user,
                "import-schedule:" + list,
                key,
                input,
                clock.instant(),
                ImportResult.class,
                connection -> {
                    VersionPreconditions.check(
                        match,
                        shopping.list(connection, user, list).version()
                    );
                    var source = UserScheduleRepository.find(
                        connection,
                        user,
                        input.userScheduleId()
                    );
                    var current = occurrences.active(connection, list);
                    var recurrence =
                        input.addition() == null
                            ? current == null
                                ? AdditionRecurrence.defaults(clock.instant())
                                : current.addition()
                            : input.addition();
                    boolean enabled = input.enabled() == null || input.enabled();
                    String id;
                    if (
                        current != null && Objects.equals(current.sourceScheduleId(), source.id())
                    ) {
                        id = current.id();
                        try (
                            var q = connection.prepareStatement(
                                "UPDATE schedule_attachments SET enabled=?,interval_unit=?,interval_count=?,weekday=?,local_time=?,time_zone=?,recurrence_start_date=?,next_due_at=?,configuration_version=configuration_version+1,version=version+1 WHERE id=?"
                            )
                        ) {
                            settings(q, recurrence, enabled);
                            q.setString(9, id);
                            q.executeUpdate();
                        }
                    } else {
                        if (current != null) detach(connection, current.id(), clock.instant());
                        id = UUID.randomUUID().toString();
                        try (
                            var q = connection.prepareStatement(
                                "INSERT INTO schedule_attachments(enabled,interval_unit,interval_count,weekday,local_time,time_zone,recurrence_start_date,next_due_at,id,list_id,source_schedule_id,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)"
                            )
                        ) {
                            settings(q, recurrence, enabled);
                            q.setString(9, id);
                            q.setString(10, list);
                            q.setString(11, source.id());
                            q.setLong(12, clock.instant().toEpochMilli());
                            q.executeUpdate();
                        }
                        var a = occurrences.attachment(connection, id);
                        occurrences.create(
                            connection,
                            a,
                            "initial",
                            clock.instant(),
                            recurrence.date(clock.instant()),
                            recurrence.date(a.nextDueAt())
                        );
                    }
                    bump(connection, list);
                    return new ImportResult(id);
                }
            );
        });
    }

    private void settings(PreparedStatement q, AdditionRecurrence r, boolean enabled)
        throws SQLException {
        q.setBoolean(1, enabled);
        q.setString(2, r.unit());
        q.setInt(3, r.count());
        if (r.weekday() == null) q.setNull(4, Types.INTEGER);
        else q.setInt(4, r.weekday());
        q.setString(5, r.localTime().toString());
        q.setString(6, r.timeZone());
        q.setString(7, r.startDate().toString());
        q.setLong(8, r.nextAfter(clock.instant()).toEpochMilli());
    }

    public State state(String user, String list) {
        return db.read(c -> {
            access.member(c, user, list);
            var a = occurrences.active(c, list);
            if (a == null) return new State(null, null, null, null, null);
            var source = source(c, a.sourceScheduleId());
            return new State(
                a,
                occurrences.latest(c, a.id()),
                source.templateId(),
                source.anchorDate(),
                source.version()
            );
        });
    }

    public static ConfiguredUserSchedule source(Connection c, String id) throws SQLException {
        try (var q = c.prepareStatement("SELECT user_id FROM user_schedules WHERE id=?")) {
            q.setString(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw ApiErrors.notFound();
                return UserScheduleRepository.find(c, r.getString(1), id);
            }
        }
    }

    public void detach(String user, String list, String match) {
        db.write(c -> {
            access.owner(c, user, list);
            VersionPreconditions.check(match, shopping.list(c, user, list).version());
            var a = occurrences.active(c, list);
            if (a != null) {
                detach(c, a.id(), clock.instant());
                bump(c, list);
            }
            return null;
        });
    }

    public static void detach(Connection c, String attachment, Instant now) throws SQLException {
        try (
            var q = c.prepareStatement(
                "UPDATE schedule_attachments SET detached_at=?,enabled=0,source_schedule_id=NULL,version=version+1 WHERE id=? AND detached_at IS NULL"
            )
        ) {
            q.setLong(1, now.toEpochMilli());
            q.setString(2, attachment);
            q.executeUpdate();
        }
    }

    public static void bump(Connection c, String list) throws SQLException {
        try (var q = c.prepareStatement("UPDATE shopping_lists SET version=version+1 WHERE id=?")) {
            q.setString(1, list);
            q.executeUpdate();
        }
    }

    public UserScheduleService.DatedMeals meals(
        String user,
        String list,
        LocalDate from,
        LocalDate to,
        String zone
    ) {
        var state = state(user, list);
        QueryRangePolicy.validate(from, to, zone, clock);
        var snapshot = content.snapshot();
        if (state.attachment() == null) return new UserScheduleService.DatedMeals(
            snapshot.revision(),
            zone,
            List.of()
        );
        var template = snapshot.templates().get(state.templateId());
        if (template == null) throw ApiErrors.invalid("Schedule content is unavailable");
        return new UserScheduleService.DatedMeals(
            snapshot.revision(),
            zone,
            from
                .datesUntil(to)
                .map(d -> new MealDateResolver().resolve(template, state.anchorDate(), d))
                .toList()
        );
    }

    public record Preview(
        LocalDate coverageStartDate,
        LocalDate coverageEndDateExclusive,
        LocalDate previouslyCoveredEndDate,
        LocalDate displayedFrom,
        LocalDate displayedTo,
        boolean isPartial,
        boolean beyondHorizon,
        ShoppingCoveragePlanner.PlanView plan
    ) {}

    public Preview preview(String user, String list, String zone) {
        var state = state(user, list);
        QueryRangePolicy.validate(
            LocalDate.now(clock),
            LocalDate.now(clock).plusDays(1),
            zone,
            clock
        );
        if (state.attachment() == null) return new Preview(
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            null
        );
        var a = state.attachment();
        var from = a.addition().date(a.nextDueAt());
        var to = a.addition().date(a.addition().nextAfter(a.nextDueAt()));
        var horizon = LocalDate.now(clock.withZone(ZoneId.of(zone)))
            .plusMonths(1)
            .plusDays(1);
        if (!from.isBefore(horizon)) return new Preview(
            from,
            to,
            a.lastCoveredEndDateExclusive(),
            null,
            null,
            true,
            true,
            null
        );
        LocalDate end = to;
        if (end.isAfter(from.plusMonths(1))) end = from.plusMonths(1);
        if (end.isAfter(horizon)) end = horizon;
        QueryRangePolicy.validate(from, end, zone, clock);
        var snapshot = content.snapshot();
        var plan = new ShoppingCoveragePlanner().plan(
            snapshot,
            state.templateId(),
            state.anchorDate(),
            from,
            end,
            a.lastCoveredEndDateExclusive()
        );
        return new Preview(
            from,
            to,
            a.lastCoveredEndDateExclusive(),
            from,
            end,
            !end.equals(to),
            false,
            ShoppingCoveragePlanner.view(plan, snapshot)
        );
    }
}
