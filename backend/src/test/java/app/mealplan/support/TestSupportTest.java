package app.mealplan.support;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class TestSupportTest {
 @Test void sqliteClockAndPrincipalAreIsolated(@TempDir Path dir) throws Exception {
  try(var c=SqliteTestSupport.open(dir.resolve("test.sqlite"));var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_keys")) { assertThat(r.getInt(1)).isEqualTo(1); }
  var clock=new MutableClock(Instant.EPOCH); clock.advance(Duration.ofDays(1)); assertThat(clock.instant()).isEqualTo(Instant.EPOCH.plusSeconds(86400));
  assertThat(SqliteTestSupport.principal("fixture").getName()).isEqualTo("fixture");
 }
}
