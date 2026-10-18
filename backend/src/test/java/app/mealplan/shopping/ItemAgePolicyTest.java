package app.mealplan.shopping;
import app.mealplan.schedule.ScheduleShoppingSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
class ItemAgePolicyTest extends ScheduleShoppingSupport {
 String add(String list,String key) throws Exception {return json(mvc.perform(post("/api/lists/"+list+"/entries").with(user("owner")).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content("{\"name\":\""+key+"\"}")).andExpect(status().isCreated()).andReturn()).get("id").asString();}
 @Test void independentExactAgeAndExtensionCannotResurrectItems() throws Exception {
  String list=list();String old=add(list,"old");mvc.perform(patch("/api/lists/"+list+"/entries/"+old).with(user("owner")).with(csrf()).header("If-Match","\"0\"").contentType("application/json").content("{\"bought\":true}")).andExpect(status().isOk());clock.advance(java.time.Duration.ofDays(2));String recent=add(list,"new");clock.advance(java.time.Duration.ofDays(5));
  mvc.perform(get("/api/lists/"+list+"/entries").with(user("owner"))).andExpect(jsonPath("$.entries.length()").value(1)).andExpect(jsonPath("$.entries[0].id").value(recent));
  mvc.perform(put("/api/lists/"+list+"/archive-policy").with(user("owner")).with(csrf()).header("If-Match","\"0\"").contentType("application/json").content("{\"ageLimit\":{\"count\":2,\"unit\":\"week\"}}")).andExpect(status().isOk());
  mvc.perform(get("/api/lists/"+list+"/archive").with(user("owner"))).andExpect(jsonPath("$.entries.length()").value(1));
  mvc.perform(put("/api/lists/"+list+"/archive-policy").with(user("owner")).with(csrf()).header("If-Match","\"1\"").contentType("application/json").content("{\"ageLimit\":{\"count\":1,\"unit\":\"day\"}}")).andExpect(status().isOk());
  mvc.perform(get("/api/lists/"+list+"/archive").with(user("owner"))).andExpect(jsonPath("$.entries.length()").value(2));
 }
}
