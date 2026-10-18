package app.mealplan.domain.schedule;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class QueryRangePolicyTest {
 @Test void calendarMonthClampingAndViewerMidnightDetermineInclusiveHorizon(){
  Clock clock=Clock.fixed(Instant.parse("2026-01-31T10:30:00Z"),ZoneOffset.UTC);
  QueryRangePolicy.validate(LocalDate.parse("2026-01-31"),LocalDate.parse("2026-02-28"),"UTC",clock);
  assertThatThrownBy(()->QueryRangePolicy.validate(LocalDate.parse("2026-01-31"),LocalDate.parse("2026-03-01"),"UTC",clock)).isInstanceOf(RuntimeException.class);
  QueryRangePolicy.validate(LocalDate.parse("2026-03-01"),LocalDate.parse("2026-03-02"),"Pacific/Kiritimati",clock);
  assertThatThrownBy(()->QueryRangePolicy.validate(LocalDate.parse("2026-03-01"),LocalDate.parse("2026-03-02"),"Pacific/Honolulu",clock)).hasMessageContaining("one calendar month ahead");
  QueryRangePolicy.validate(LocalDate.parse("2026-02-28"),LocalDate.parse("2026-03-01"),"Pacific/Honolulu",clock);
 }
}
