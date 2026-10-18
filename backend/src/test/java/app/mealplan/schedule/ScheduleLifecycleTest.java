package app.mealplan.schedule;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
class ScheduleLifecycleTest extends ScheduleShoppingSupport {
 @org.springframework.beans.factory.annotation.Autowired ScheduleGenerationService generation;
 @org.springframework.beans.factory.annotation.Autowired UserScheduleDeletionService deletion;
 @org.springframework.beans.factory.annotation.Autowired OccurrenceRepository occurrences;
 @Test void deletingListCascadesScheduleHistoryButKeepsPersonalSource() throws Exception {
  String list=list(),source=source("2026-10-20");var result=json(configure(list,source,"delete-list",0,"").andExpect(status().isOk()).andReturn());String occurrence=result.get("latest").get("id").asString();
  mvc.perform(delete("/api/lists/"+list).with(user("owner")).with(csrf()).header("If-Match","\"1\"")).andExpect(status().isNoContent());
  mvc.perform(get("/api/user-schedules/"+source).with(user("owner"))).andExpect(status().isOk()).andExpect(jsonPath("$.linkedLists").isEmpty());
  mvc.perform(post("/api/lists/"+list+"/schedule/retry").with(user("owner")).with(csrf()).contentType("application/json").content("{\"occurrenceId\":\""+occurrence+"\"}")).andExpect(status().isNotFound());
  db.read(c->{try(var q=c.prepareStatement("SELECT count(*) FROM schedule_occurrences WHERE id=?")){q.setString(1,occurrence);try(var r=q.executeQuery()){assertThat(r.getInt(1)).isZero();}}return null;});
 }
 @Test void racingSourceDeletionCannotBeFollowedByNewEntries() throws Exception {
  for(int i=0;i<3;i++){
   String list=list(),source=source("2026-10-20");configure(list,source,"race",0,"").andExpect(status().isOk());String owner=identities.resolve("https://accounts.google.com","owner","owner","owner@example.test").id();
   String pending=db.write(c->{var a=occurrences.active(c,list);return occurrences.create(c,a,"recurring",a.nextDueAt(),java.time.LocalDate.of(2026,10,24),java.time.LocalDate.of(2026,10,31)).id();});
   var start=new java.util.concurrent.CountDownLatch(1);
   try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
    var remove=executor.submit(()->{start.await();deletion.delete(owner,source,"\"0\"");return true;});var run=executor.submit(()->{start.await();generation.generate(pending);return true;});start.countDown();remove.get();int afterDeletion=count(list);run.get();assertThat(count(list)).isEqualTo(afterDeletion);
   }
   mvc.perform(get("/api/lists/"+list+"/schedule").with(user("owner"))).andExpect(jsonPath("$.attachment").isEmpty());
  }
 }

 @Test void deletingSourceDetachesAllListsPreservingPurchasesAndReplacementLeavesOneLink() throws Exception {
  String source=source("2026-10-20"),a=list(),b=list();configure(a,source,"a",0,"").andExpect(status().isOk());configure(b,source,"b",0,"").andExpect(status().isOk());int before=count(a);
  mvc.perform(get("/api/user-schedules/"+source).with(user("owner"))).andExpect(jsonPath("$.linkedLists.length()").value(2));
  mvc.perform(delete("/api/user-schedules/"+source).with(user("other")).with(csrf()).header("If-Match","\"0\"")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/user-schedules/"+source).with(user("owner")).with(csrf()).header("If-Match","\"0\"")).andExpect(status().isNoContent());
  for(String id:new String[]{a,b})mvc.perform(get("/api/lists/"+id+"/schedule").with(user("owner"))).andExpect(jsonPath("$.attachment").isEmpty());assertThat(count(a)).isEqualTo(before);
  String next=source("2026-10-20");configure(a,next,"new",2,"").andExpect(status().isOk());String third=source("2026-10-21");configure(a,third,"replace",3,"").andExpect(status().isOk());
  db.read(c->{try(var q=c.prepareStatement("SELECT count(*) FROM schedule_attachments WHERE list_id=? AND detached_at IS NULL")){q.setString(1,a);try(var r=q.executeQuery()){assertThat(r.getInt(1)).isEqualTo(1);}}return null;});
 }
}
