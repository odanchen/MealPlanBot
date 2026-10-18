package app.mealplan.schedule;

import app.mealplan.domain.schedule.AdditionRecurrence;
import java.time.*;

public record ScheduleAttachment(
    String id,
    String listId,
    String sourceScheduleId,
    Instant detachedAt,
    boolean enabled,
    AdditionRecurrence addition,
    Instant nextDueAt,
    long configurationVersion,
    long version,
    Instant createdAt,
    LocalDate lastCoveredStartDate,
    LocalDate lastCoveredEndDateExclusive
) {}
