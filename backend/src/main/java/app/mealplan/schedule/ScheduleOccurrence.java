package app.mealplan.schedule;
import java.time.*;
public record ScheduleOccurrence(String id,String attachmentId,String kind,Instant scheduledAt,long configurationVersion,LocalDate coverageStartDate,LocalDate coverageEndDateExclusive,String timeZone,String contentRevision,Long sourceScheduleVersion,String effectiveTemplateId,LocalDate effectiveAnchorDate,String status,String failureCode,Instant completedAt) {}
