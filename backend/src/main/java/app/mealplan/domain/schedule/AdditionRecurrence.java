package app.mealplan.domain.schedule;

import java.time.*;
import java.time.temporal.*;

/** Calendar recurrence. Gaps use the first valid local instant; repeats use the first offset. */
public record AdditionRecurrence(
    String unit,
    int count,
    Integer weekday,
    LocalTime localTime,
    String timeZone,
    LocalDate startDate
) {
    public AdditionRecurrence {
        if (
            !java.util.Set.of("day", "week").contains(unit) ||
            count <= 0 ||
            localTime == null ||
            startDate == null ||
            timeZone == null ||
            !ZoneId.getAvailableZoneIds().contains(timeZone)
        ) throw new IllegalArgumentException(
            "Choose positive day/week recurrence, local time, start date and IANA zone"
        );
        if (
            (unit.equals("week") && (weekday == null || weekday < 1 || weekday > 7)) ||
            (unit.equals("day") && weekday != null)
        ) throw new IllegalArgumentException(
            "Weekly recurrence requires a weekday; daily recurrence has none"
        );
    }

    public static AdditionRecurrence defaults(Instant now) {
        return new AdditionRecurrence(
            "week",
            1,
            6,
            LocalTime.of(9, 0),
            "America/Toronto",
            now.atZone(ZoneId.of("America/Toronto")).toLocalDate()
        );
    }

    private long step() {
        return Math.multiplyExact((long) count, unit.equals("week") ? 7 : 1);
    }

    private LocalDate base() {
        return weekday == null
            ? startDate
            : startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday)));
    }

    public Instant at(LocalDate date) {
        var local = date.atTime(localTime);
        var zone = ZoneId.of(timeZone);
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) return zone
            .getRules()
            .getTransition(local)
            .getDateTimeAfter()
            .atZone(zone)
            .toInstant();
        return local.atOffset(offsets.getFirst()).toInstant();
    }

    public Instant nextAfter(Instant instant) {
        LocalDate date = instant.atZone(ZoneId.of(timeZone)).toLocalDate(),
            base = base();
        long days = ChronoUnit.DAYS.between(base, date);
        LocalDate candidate =
            days <= 0
                ? base
                : base.plusDays(
                      Math.multiplyExact(Math.floorDiv(days + step() - 1, step()), step())
                  );
        if (!at(candidate).isAfter(instant)) candidate = candidate.plusDays(step());
        return at(candidate);
    }

    public Instant latestAtOrBefore(Instant instant) {
        LocalDate date = instant.atZone(ZoneId.of(timeZone)).toLocalDate(),
            base = base();
        long days = ChronoUnit.DAYS.between(base, date);
        if (days < 0) return null;
        LocalDate candidate = base.plusDays(Math.multiplyExact(days / step(), step()));
        if (at(candidate).isAfter(instant)) candidate = candidate.minusDays(step());
        return candidate.isBefore(base) ? null : at(candidate);
    }

    public LocalDate date(Instant instant) {
        return instant.atZone(ZoneId.of(timeZone)).toLocalDate();
    }
}
