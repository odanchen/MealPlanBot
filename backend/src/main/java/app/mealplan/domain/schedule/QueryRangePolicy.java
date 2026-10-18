package app.mealplan.domain.schedule;
import java.time.*;
import app.mealplan.web.ApiErrors;
public final class QueryRangePolicy {
 public static void validate(LocalDate from,LocalDate to,String timeZone,Clock clock){
  if(timeZone==null||!(ZoneId.getAvailableZoneIds().contains(timeZone)||timeZone.equals("UTC")))throw ApiErrors.invalid("A valid viewer IANA timeZone is required");
  LocalDate today=LocalDate.now(clock.withZone(ZoneId.of(timeZone)));
  if(from==null||to==null||!from.isBefore(to)||to.isAfter(from.plusMonths(1)))throw ApiErrors.invalid("Date range must be positive and at most one calendar month");
  if(to.minusDays(1).isAfter(today.plusMonths(1)))throw ApiErrors.invalid("Dates may be no later than one calendar month ahead of your local today");
 }
}
