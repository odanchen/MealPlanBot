package app.mealplan.identity;
import app.mealplan.MealPlanApplication;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(classes=MealPlanApplication.class)
@ActiveProfiles("local")
@AutoConfigureMockMvc
class LocalFixtureAuthenticationTest {
 static final Path DIRECTORY;
 static {try{DIRECTORY=Files.createTempDirectory("mealplan-local-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("mealplan.database-path",() -> DIRECTORY.resolve(".local/mealplan/app.sqlite").toString());}
 @Autowired MockMvc mvc;
 @Test void localSignInUsesOnlyOneStableUserAndLogoutEndsAccess() throws Exception {
  mvc.perform(get("/api/session")).andExpect(jsonPath("$.authenticated").value(false)).andExpect(jsonPath("$.authMode").value("local"));
  mvc.perform(post("/api/local-session")).andExpect(status().isForbidden());
  var result=mvc.perform(post("/api/local-session").with(csrf())).andExpect(status().isNoContent()).andReturn();
  var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false);
  mvc.perform(get("/api/session").session(session)).andExpect(jsonPath("$.userId").value(LocalFixtureAuthentication.USER_ID));
  mvc.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isNoContent());
  org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();
  mvc.perform(get("/api/lists")).andExpect(status().isUnauthorized());
 }
 @Test void nonloopbackCannotUseFixtureAndNoGoogleRoutesExist() throws Exception {
  mvc.perform(post("/api/local-session").with(csrf()).with(req -> {req.setRemoteAddr("192.0.2.1");return req;})).andExpect(status().isForbidden());
  mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isNotFound());
 }
}
