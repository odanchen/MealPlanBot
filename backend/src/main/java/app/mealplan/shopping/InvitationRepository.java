package app.mealplan.shopping;
import java.sql.*;
import java.time.*;
import java.util.*;
import app.mealplan.web.ApiErrors;
import org.springframework.stereotype.Repository;
@Repository
public class InvitationRepository {
 public Invitation create(Connection c,String list,String user,String hash,Instant now) throws SQLException {
  String id=UUID.randomUUID().toString();try(var q=c.prepareStatement("INSERT INTO invitations(id,list_id,token_hash,created_by,created_at,expires_at) VALUES(?,?,?,?,?,?)")){q.setString(1,id);q.setString(2,list);q.setString(3,hash);q.setString(4,user);q.setLong(5,now.toEpochMilli());q.setLong(6,now.plus(Duration.ofHours(72)).toEpochMilli());q.executeUpdate();}return get(c,list,id);
 }
 public Invitation get(Connection c,String list,String id) throws SQLException {try(var q=c.prepareStatement("SELECT * FROM invitations WHERE list_id=? AND id=?")){q.setString(1,list);q.setString(2,id);try(var r=q.executeQuery()){if(!r.next())throw ApiErrors.notFound();return row(r);}}}
 public Invitation usable(Connection c,String hash,Instant now) throws SQLException {
  try(var q=c.prepareStatement("SELECT i.* FROM invitations i JOIN shopping_lists l ON l.id=i.list_id WHERE i.token_hash=? AND i.withdrawn_at IS NULL AND i.expires_at>?")){q.setString(1,hash);q.setLong(2,now.toEpochMilli());try(var r=q.executeQuery()){if(!r.next())throw unusable();return row(r);}}
 }
 public List<Invitation> list(Connection c,String list) throws SQLException {try(var q=c.prepareStatement("SELECT * FROM invitations WHERE list_id=? ORDER BY created_at DESC,id")){q.setString(1,list);try(var r=q.executeQuery()){var result=new ArrayList<Invitation>();while(r.next())result.add(row(r));return result;}}}
 public void withdraw(Connection c,String list,String id,Instant now) throws SQLException {get(c,list,id);try(var q=c.prepareStatement("UPDATE invitations SET withdrawn_at=COALESCE(withdrawn_at,?) WHERE list_id=? AND id=?")){q.setLong(1,now.toEpochMilli());q.setString(2,list);q.setString(3,id);q.executeUpdate();}}
 public static ApiErrors.Failure unusable(){return new ApiErrors.Failure(404,"unusable_invitation","This invitation is unavailable or has expired. Ask the owner for a new link.",null);}
 private Invitation row(ResultSet r) throws SQLException {long withdrawn=r.getLong("withdrawn_at");Instant at=r.wasNull()?null:Instant.ofEpochMilli(withdrawn);return new Invitation(r.getString("id"),r.getString("list_id"),r.getString("created_by"),Instant.ofEpochMilli(r.getLong("created_at")),Instant.ofEpochMilli(r.getLong("expires_at")),at);}
}
