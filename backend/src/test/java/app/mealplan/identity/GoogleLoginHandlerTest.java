package app.mealplan.identity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GoogleLoginHandlerTest {
 @Test void acceptsOnlySameOriginPaths() {
  for(String input:new String[]{"https://evil.test/","//evil.test/","/\\evil.test","/%2fevil.test/","/api/lists","/oauth2/authorization/google","/login/oauth2/code/google","/\r\nLocation:evil"}) assertThat(GoogleLoginHandler.safeReturnPath(input)).isEqualTo("/");
  assertThat(GoogleLoginHandler.safeReturnPath("/schedules/abc?date=2026-10-18")).isEqualTo("/schedules/abc?date=2026-10-18");
 }
}
