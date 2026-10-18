package app.mealplan.schedule;
import app.mealplan.persistence.*;
import app.mealplan.web.*;
import java.time.*;
import java.util.*;
import java.sql.*;
import org.springframework.stereotype.Repository;
@Repository
public class UserScheduleRepository {
 private final SqliteDatabase db;
 public UserScheduleRepository(SqliteDatabase db,MigrationRunner migrations){this.db=db;}
 public List<ConfiguredUserSchedule> list(String owner){return db.read(c->{try(var q=c.prepareStatement("SELECT * FROM user_schedules WHERE user_id=? ORDER BY created_at,id")){q.setString(1,owner);try(var rows=q.executeQuery()){var result=new ArrayList<ConfiguredUserSchedule>();while(rows.next())result.add(row(rows));return result;}}});}
 public ConfiguredUserSchedule get(String owner,String id){return db.read(c->find(c,owner,id));}
 public ConfiguredUserSchedule create(String owner,String template,LocalDate anchor,Instant now){return db.write(c->{String id=UUID.randomUUID().toString();try(var q=c.prepareStatement("INSERT INTO user_schedules(id,user_id,template_id,anchor_date,created_at,updated_at,version) VALUES(?,?,?,?,?,?,0)")){q.setString(1,id);q.setString(2,owner);q.setString(3,template);q.setString(4,anchor.toString());q.setLong(5,now.toEpochMilli());q.setLong(6,now.toEpochMilli());q.executeUpdate();}return find(c,owner,id);});}
 public ConfiguredUserSchedule update(String owner,String id,LocalDate anchor,String match,Instant now){return db.write(c->{var current=find(c,owner,id);VersionPreconditions.check(match,current.version());if(current.anchorDate().equals(anchor))return current;
  try(var q=c.prepareStatement("UPDATE user_schedules SET anchor_date=?,updated_at=?,version=version+1 WHERE id=? AND user_id=?")){q.setString(1,anchor.toString());q.setLong(2,now.toEpochMilli());q.setString(3,id);q.setString(4,owner);q.executeUpdate();}try(var q=c.prepareStatement("UPDATE schedule_attachments SET last_covered_start_date=NULL,last_covered_end_date_exclusive=NULL,version=version+1 WHERE source_schedule_id=? AND detached_at IS NULL")){q.setString(1,id);q.executeUpdate();}return find(c,owner,id);
 });}
 public static ConfiguredUserSchedule find(Connection c,String owner,String id) throws SQLException {try(var q=c.prepareStatement("SELECT * FROM user_schedules WHERE id=? AND user_id=?")){q.setString(1,id);q.setString(2,owner);try(var r=q.executeQuery()){if(!r.next())throw ApiErrors.notFound();return row(r);}}}
 private static ConfiguredUserSchedule row(ResultSet r) throws SQLException {return new ConfiguredUserSchedule(r.getString("id"),r.getString("user_id"),r.getString("template_id"),LocalDate.parse(r.getString("anchor_date")),Instant.ofEpochMilli(r.getLong("created_at")),Instant.ofEpochMilli(r.getLong("updated_at")),r.getLong("version"));}
}
