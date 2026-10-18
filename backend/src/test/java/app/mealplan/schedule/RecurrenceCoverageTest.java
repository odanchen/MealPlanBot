package app.mealplan.schedule;
import app.mealplan.domain.schedule.*;
import app.mealplan.content.ContentLoader;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RecurrenceCoverageTest {
 @Test void calendarIntervalsDstAndStrictNextDue(){
  var weekly=AdditionRecurrence.defaults(Instant.parse("2026-10-20T16:00:00Z"));assertThat(weekly.nextAfter(Instant.parse("2026-10-20T16:00:00Z"))).isEqualTo(Instant.parse("2026-10-24T13:00:00Z"));assertThat(weekly.nextAfter(Instant.parse("2026-10-24T13:00:00Z"))).isEqualTo(Instant.parse("2026-10-31T13:00:00Z"));
  var gap=new AdditionRecurrence("day",1,null,LocalTime.of(2,30),"America/Toronto",LocalDate.of(2026,3,7));assertThat(gap.at(LocalDate.of(2026,3,8))).isEqualTo(Instant.parse("2026-03-08T07:00:00Z"));
  var repeat=new AdditionRecurrence("day",1,null,LocalTime.of(1,30),"America/Toronto",LocalDate.of(2026,10,31));assertThat(repeat.at(LocalDate.of(2026,11,1))).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
  var fortnight=new AdditionRecurrence("week",2,6,LocalTime.of(9,0),"America/Toronto",LocalDate.of(2026,10,20));assertThat(fortnight.nextAfter(Instant.parse("2026-10-24T13:00:00Z"))).isEqualTo(Instant.parse("2026-11-07T14:00:00Z"));assertThat(fortnight.latestAtOrBefore(Instant.parse("2026-11-20T00:00:00Z"))).isEqualTo(Instant.parse("2026-11-07T14:00:00Z"));
 }
 @Test void plannerClipsAnchorAndPriorCoverageWithoutGoingBackward() throws Exception {
  var content=ContentLoader.load(ContentLoader.defaultRoot());var planner=new ShoppingCoveragePlanner();var anchor=LocalDate.of(2026,10,20);
  var plan=planner.plan(content,"three-week",anchor,anchor,anchor.plusDays(7),anchor.plusDays(4));assertThat(plan.from()).isEqualTo(anchor.plusDays(4));assertThat(plan.meals()).hasSize(3);assertThat(plan.ingredients()).isNotEmpty();
  assertThat(planner.plan(content,"three-week",anchor,anchor,anchor.plusDays(2),anchor.plusDays(4)).ingredients()).isEmpty();
  assertThat(planner.plan(content,"three-week",anchor,anchor.minusDays(5),anchor,null).meals()).isEmpty();
 }
}
