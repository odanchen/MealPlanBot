package app.mealplan.web;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class VersionPreconditionsTest {
 @Test void rejectsAbsentMalformedAndStaleVersions() {
  for(String input:new String[]{null,"1","W/\"1\"","\"-1\""}) assertThatThrownBy(() -> VersionPreconditions.require(input)).isInstanceOf(ApiErrors.Failure.class);
  assertThat(VersionPreconditions.require("\"12\"")).isEqualTo(12);
  var conflict=catchThrowableOfType(ApiErrors.Failure.class,() -> VersionPreconditions.check("\"1\"",2)); assertThat(conflict.status).isEqualTo(409);assertThat(conflict.currentVersion).isEqualTo(2);
 }
}
