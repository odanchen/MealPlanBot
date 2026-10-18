package app.mealplan.schedule;
import app.mealplan.persistence.*;
import app.mealplan.web.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class UserScheduleDeletionService {
 private final SqliteDatabase db;private final Clock clock;
 public UserScheduleDeletionService(SqliteDatabase db,MigrationRunner migrations,Clock clock){this.db=db;this.clock=clock;}
 public List<Map<String,String>> linked(String owner,String id){return db.read(c->{UserScheduleRepository.find(c,owner,id);try(var q=c.prepareStatement("SELECT l.id,l.name FROM shopping_lists l JOIN schedule_attachments a ON a.list_id=l.id WHERE a.source_schedule_id=? AND a.detached_at IS NULL ORDER BY l.id")){q.setString(1,id);try(var r=q.executeQuery()){var list=new ArrayList<Map<String,String>>();while(r.next())list.add(Map.of("id",r.getString(1),"name",r.getString(2)));return list;}}});}
 public void delete(String owner,String id,String match){db.write(c->{var source=UserScheduleRepository.find(c,owner,id);VersionPreconditions.check(match,source.version());var ids=new ArrayList<String>();try(var q=c.prepareStatement("SELECT id,list_id FROM schedule_attachments WHERE source_schedule_id=? AND detached_at IS NULL")){q.setString(1,id);try(var r=q.executeQuery()){while(r.next()){ids.add(r.getString(1));ScheduleAttachmentService.bump(c,r.getString(2));}}}for(String attachment:ids)ScheduleAttachmentService.detach(c,attachment,clock.instant());try(var q=c.prepareStatement("DELETE FROM user_schedules WHERE id=?")){q.setString(1,id);q.executeUpdate();}return null;});}
}
