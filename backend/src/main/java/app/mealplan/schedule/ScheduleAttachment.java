package app.mealplan.schedule;
import java.time.*;
import app.mealplan.domain.schedule.AdditionRecurrence;
public record ScheduleAttachment(String id,String listId,String sourceScheduleId,Instant detachedAt,boolean enabled,AdditionRecurrence addition,Instant nextDueAt,long configurationVersion,long version,Instant createdAt,LocalDate lastCoveredStartDate,LocalDate lastCoveredEndDateExclusive) {}
