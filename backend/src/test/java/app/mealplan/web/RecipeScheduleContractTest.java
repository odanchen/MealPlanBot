package app.mealplan.web;
import app.mealplan.MealPlanApplication;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(classes=MealPlanApplication.class,properties={"mealplan.deployment-target=test","mealplan.fixture-auth=false"})
@ActiveProfiles("test") @AutoConfigureMockMvc
class RecipeScheduleContractTest {
 static final Path DIR;
 static {try {DIR=Files.createTempDirectory("recipe-contract-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("mealplan.database-path",()->DIR.resolve("app.sqlite").toString());}
 @Autowired MockMvc mvc;
 @Test void recipesArePublicAndSchedulesArePrivate() throws Exception {
  mvc.perform(get("/api/recipes")).andExpect(status().isOk()).andExpect(jsonPath("$.recipes.length()").value(16));
  mvc.perform(get("/api/recipes/red-wine-beef-stew-with-mashed-potatoes")).andExpect(status().isOk()).andExpect(jsonPath("$.recipe.title").value("Red Wine Beef Stew with Mashed Potatoes")).andExpect(jsonPath("$.recipe.images").isEmpty());
  mvc.perform(get("/api/recipes/1")).andExpect(status().isNotFound());
  mvc.perform(get("/api/schedule-templates")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/user-schedules")).andExpect(status().isUnauthorized());
 }

 @Autowired app.mealplan.identity.IdentityService identities;
 private org.springframework.test.web.servlet.request.RequestPostProcessor owner(String subject){var user=identities.resolve("https://accounts.google.com",subject,subject,subject+"@example.test");return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(app.mealplan.support.SqliteTestSupport.principal(user.id()));}
 @Test void personalScheduleSupportsAnyAnchorPrivacyAndOptimisticUpdates() throws Exception {
  var alice=owner("alice");var bob=owner("bob");
  var created=mvc.perform(post("/api/user-schedules").with(alice).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"templateId\":\"three-week\",\"anchorDate\":\"2026-01-07\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0)).andReturn();
  var json=new tools.jackson.databind.json.JsonMapper().readTree(created.getResponse().getContentAsString());String path="/api/user-schedules/"+json.get("id").asString();
  mvc.perform(get(path).with(bob)).andExpect(status().isNotFound());mvc.perform(get(path+"/meals").with(bob).param("from","2026-01-07").param("to","2026-01-08").param("timeZone","UTC")).andExpect(status().isNotFound());
  mvc.perform(get(path+"/meals").with(alice).param("from","2026-01-06").param("to","2026-01-28").param("timeZone","America/Toronto")).andExpect(status().isOk()).andExpect(jsonPath("$.meals.length()").value(22)).andExpect(jsonPath("$.meals[0].status").value("not_started")).andExpect(jsonPath("$.meals[0].cycleDay").isEmpty()).andExpect(jsonPath("$.meals[1].cycleDay").value(1)).andExpect(jsonPath("$.meals[6].status").value("rest")).andExpect(jsonPath("$.meals[8].cycleDay").value(8));
  String previous=null;
  for(String zone:new String[]{"America/Toronto","Pacific/Kiritimati","Pacific/Honolulu"}){
   var result=mvc.perform(get(path+"/meals").with(alice).param("from","2026-01-07").param("to","2026-01-08").param("timeZone",zone)).andExpect(status().isOk()).andReturn();
   String meals=new tools.jackson.databind.json.JsonMapper().readTree(result.getResponse().getContentAsString()).get("meals").toString();if(previous!=null)org.assertj.core.api.Assertions.assertThat(meals).isEqualTo(previous);previous=meals;
  }
  for(String zone:new String[]{"Invalid/Zone","+01:00",""})mvc.perform(get(path+"/meals").with(alice).param("from","2026-01-07").param("to","2026-01-08").param("timeZone",zone)).andExpect(status().isUnprocessableContent());
  mvc.perform(get(path+"/meals").with(alice).param("from","2026-01-07").param("to","2026-01-08")).andExpect(status().isUnprocessableContent());
  mvc.perform(get(path+"/meals").with(alice).param("from","2026-01-31").param("to","2026-03-01").param("timeZone","UTC")).andExpect(status().isUnprocessableContent());
  mvc.perform(get(path+"/meals").with(alice).param("from","2999-01-01").param("to","2999-01-02").param("timeZone","UTC")).andExpect(status().isUnprocessableContent());
  mvc.perform(patch(path).with(alice).contentType("application/json").header("If-Match","\"0\"").content("{\"anchorDate\":\"2999-01-01\"}")).andExpect(status().isForbidden());
  mvc.perform(patch(path).with(bob).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").header("If-Match","\"0\"").content("{\"anchorDate\":\"2999-01-01\"}")).andExpect(status().isNotFound());
  mvc.perform(patch(path).with(alice).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").header("If-Match","\"0\"").content("{\"anchorDate\":\"2999-01-01\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(patch(path).with(alice).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").header("If-Match","\"0\"").content("{\"anchorDate\":\"2026-01-01\"}")).andExpect(status().isConflict());
  mvc.perform(get(path).with(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.schedule.anchorDate").value("2999-01-01")).andExpect(header().string("Cache-Control","no-store"));
 }
}
