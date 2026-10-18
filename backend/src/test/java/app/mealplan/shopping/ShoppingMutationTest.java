package app.mealplan.shopping;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
class ShoppingMutationTest extends PrivateListTest {
 @Test void retriesConflictsAndDeletedEntries() throws Exception {
  String list=create("alice","mutations"),path="/api/lists/"+list+"/entries";
  org.assertj.core.api.Assertions.assertThat(create("alice","mutations")).isEqualTo(list);
  String body="{\"name\":\"Milk\"}";
  String entry=id(mvc.perform(add(list,"milk",body)).andExpect(status().isCreated()).andReturn());
  org.assertj.core.api.Assertions.assertThat(id(mvc.perform(add(list,"milk",body)).andExpect(status().isCreated()).andReturn())).isEqualTo(entry);
  mvc.perform(add(list,"milk","{\"name\":\"Bread\"}")).andExpect(status().isConflict());
  mvc.perform(add(list,"another-milk",body)).andExpect(status().isCreated());
  mvc.perform(patch(path+"/"+entry).with(user("alice")).with(csrf()).header("If-Match","\"0\"").contentType("application/json").content("{\"bought\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(patch(path+"/"+entry).with(user("alice")).with(csrf()).header("If-Match","\"1\"").contentType("application/json").content("{\"bought\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(delete(path+"/"+entry).with(user("alice")).with(csrf()).header("If-Match","\"0\"")).andExpect(status().isConflict());
  mvc.perform(delete(path+"/"+entry).with(user("alice")).with(csrf()).header("If-Match","\"1\"")).andExpect(status().isNoContent());
  mvc.perform(patch(path+"/"+entry).with(user("alice")).with(csrf()).header("If-Match","\"1\"").contentType("application/json").content("{\"bought\":false}")).andExpect(status().isNotFound());
 }
}
