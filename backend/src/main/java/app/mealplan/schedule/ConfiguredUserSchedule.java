package app.mealplan.schedule;
import java.time.*;
public record ConfiguredUserSchedule(String id,String userId,String templateId,LocalDate anchorDate,Instant createdAt,Instant updatedAt,long version) {}
