package app.mealplan.persistence;
import java.sql.*;
import java.time.*;
import app.mealplan.web.ApiErrors;
import org.springframework.stereotype.Repository;
@Repository
public class RequestDeduplicationRepository {
 public static final Duration RETENTION=Duration.ofDays(7);
 public String find(Connection c,String user,String operation,String key,String hash,Instant now) throws SQLException {
  try(var q=c.prepareStatement("DELETE FROM request_deduplication WHERE created_at<=?")){q.setLong(1,now.minus(RETENTION).toEpochMilli());q.executeUpdate();}
  try(var q=c.prepareStatement("SELECT payload_hash,result_json FROM request_deduplication WHERE user_id=? AND operation=? AND request_id=?")){
   q.setString(1,user);q.setString(2,operation);q.setString(3,key);try(var r=q.executeQuery()){
    if(!r.next())return null;
    if(!hash.equals(r.getString(1)))throw new ApiErrors.Failure(409,"idempotency_conflict","This request ID was already used with different input",null);
    return r.getString(2);
   }
  }
 }
 public void save(Connection c,String user,String operation,String key,String hash,String result,Instant now) throws SQLException {
  try(var q=c.prepareStatement("INSERT INTO request_deduplication VALUES(?,?,?,?,?,?)")){q.setString(1,user);q.setString(2,operation);q.setString(3,key);q.setString(4,hash);q.setString(5,result);q.setLong(6,now.toEpochMilli());q.executeUpdate();}
 }
}
