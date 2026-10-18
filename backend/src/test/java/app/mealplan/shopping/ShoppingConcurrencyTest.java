package app.mealplan.shopping;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
class ShoppingConcurrencyTest extends SharingHttpSupport {
 @Test void differentEntryWritesSurviveAndStaleSameEntryWritesConflict() throws Exception {
  String list=list();accept("member",token(invite(list))).andExpect(status().isOk());String a=entry(list,"owner","A"),b=entry(list,"member","B");
  try(var executor=Executors.newFixedThreadPool(2)){
   var first=executor.submit(()->mvc.perform(patch("/api/lists/"+list+"/entries/"+a).with(user("owner")).with(csrf()).header("If-Match","\"0\"").contentType("application/json").content("{\"bought\":true}")).andReturn().getResponse().getStatus());
   var second=executor.submit(()->mvc.perform(patch("/api/lists/"+list+"/entries/"+b).with(user("member")).with(csrf()).header("If-Match","\"0\"").contentType("application/json").content("{\"bought\":true}")).andReturn().getResponse().getStatus());
   assertThat(first.get()).isEqualTo(200);assertThat(second.get()).isEqualTo(200);
  }
  mvc.perform(get("/api/lists/"+list+"/entries").with(user("owner"))).andExpect(jsonPath("$.entries[0].boughtAt").isNotEmpty()).andExpect(jsonPath("$.entries[1].boughtAt").isNotEmpty());
  mvc.perform(patch("/api/lists/"+list+"/entries/"+a).with(user("member")).with(csrf()).header("If-Match","\"0\"").contentType("application/json").content("{\"bought\":false}")).andExpect(status().isConflict());
 }
 @Test void deletionSerializesWithAcceptanceAndItemMutationWithoutResurrection() throws Exception {
  for(int i=0;i<5;i++){
   String list=list(),token=token(invite(list)),entry=entry(list,"owner","Keep until deleted");
   var owner=user("owner");var guest=user("racing-guest");var start=new CountDownLatch(1);
   try(var executor=Executors.newFixedThreadPool(3)){
    var deletion=executor.submit(()->{start.await();return mvc.perform(delete("/api/lists/"+list).with(owner).with(csrf()).header("If-Match","\"0\"")).andReturn().getResponse().getStatus();});
    var acceptance=executor.submit(()->{start.await();return mvc.perform(post("/api/invitations/accept").with(guest).with(csrf()).contentType("application/json").content("{\"token\":\""+token+"\"}")).andReturn().getResponse().getStatus();});
    var mutation=executor.submit(()->{start.await();return mvc.perform(patch("/api/lists/"+list+"/entries/"+entry).with(owner).with(csrf()).header("If-Match","\"0\"").contentType("application/json").content("{\"bought\":true}")).andReturn().getResponse().getStatus();});
    start.countDown();assertThat(deletion.get()).isEqualTo(204);assertThat(acceptance.get()).isIn(200,404);assertThat(mutation.get()).isIn(200,404);
   }
   mvc.perform(get("/api/lists/"+list).with(user("racing-guest"))).andExpect(status().isNotFound());accept("racing-guest",token).andExpect(status().isNotFound());
   db.read(c->{for(String table:new String[]{"memberships","invitations","shopping_entries"})try(var q=c.prepareStatement("SELECT count(*) FROM "+table+" WHERE list_id=?")){q.setString(1,list);try(var r=q.executeQuery()){assertThat(r.getInt(1)).isZero();}}return null;});
  }
 }
}
