package app.mealplan.shopping;
import java.sql.*;
import java.time.*;
import java.util.*;
import app.mealplan.web.ApiErrors;
import org.springframework.stereotype.Repository;
@Repository
public class ShoppingRepository {
 private final Clock clock;
 public ShoppingRepository(){this(Clock.systemUTC());}
 @org.springframework.beans.factory.annotation.Autowired public ShoppingRepository(Clock clock){this.clock=clock;}

 public ShoppingList list(Connection c,String user,String id) throws SQLException {
  try(var q=c.prepareStatement("SELECT * FROM shopping_lists WHERE id=?")){q.setString(1,id);try(var r=q.executeQuery()){if(!r.next())throw ApiErrors.notFound();return listRow(r,user,c);}}
 }
 public List<ShoppingList> catalogue(Connection c,String user) throws SQLException {
  try(var q=c.prepareStatement("SELECT l.* FROM shopping_lists l JOIN memberships m ON l.id=m.list_id WHERE m.user_id=? ORDER BY l.name COLLATE NOCASE,l.created_at,l.id")){q.setString(1,user);try(var r=q.executeQuery()){var result=new ArrayList<ShoppingList>();while(r.next())result.add(listRow(r,user,c));return result;}}
 }
 public ShoppingList createList(Connection c,String user,String name,Instant now) throws SQLException {
  String id=UUID.randomUUID().toString();
  try(var q=c.prepareStatement("INSERT INTO shopping_lists(id,name,owner_user_id,created_at) VALUES(?,?,?,?)")){q.setString(1,id);q.setString(2,name);q.setString(3,user);q.setLong(4,now.toEpochMilli());q.executeUpdate();}
  try(var q=c.prepareStatement("INSERT INTO memberships VALUES(?,?,?)")){q.setString(1,id);q.setString(2,user);q.setLong(3,now.toEpochMilli());q.executeUpdate();}
  return list(c,user,id);
 }
 public ShoppingEntry entry(Connection c,String list,String id) throws SQLException {
  try(var q=c.prepareStatement("SELECT * FROM shopping_entries WHERE list_id=? AND id=?")){q.setString(1,list);q.setString(2,id);try(var r=q.executeQuery()){if(!r.next())throw ApiErrors.notFound();return entryRow(r);}}
 }
 public List<ShoppingEntry> entries(Connection c,String list,String state,String sort,boolean group) throws SQLException {
  String order=ShoppingEntryQueries.order(sort,group);
  try(var q=c.prepareStatement("SELECT * FROM shopping_entries WHERE list_id=? AND archived_at IS "+(state.equals("active")?"NULL":"NOT NULL")+(state.equals("active")?" AND created_at> ?-(SELECT item_age_limit_seconds*1000 FROM shopping_lists WHERE id=?)":"")+" ORDER BY "+order)){q.setString(1,list);if(state.equals("active")){q.setLong(2,clock.instant().toEpochMilli());q.setString(3,list);}try(var r=q.executeQuery()){var result=new ArrayList<ShoppingEntry>();while(r.next())result.add(entryRow(r));return result;}}
 }
 public ShoppingEntry add(Connection c,String user,String list,String name,String ingredient,String quantity,String unit,Instant now) throws SQLException {
  String id=UUID.randomUUID().toString();
  try(var q=c.prepareStatement("INSERT INTO shopping_entries(id,list_id,display_name,ingredient_id,quantity_decimal,unit,created_at,created_by) VALUES(?,?,?,?,?,?,?,?)")){q.setString(1,id);q.setString(2,list);q.setString(3,name);q.setString(4,ingredient);q.setString(5,quantity);q.setString(6,unit);q.setLong(7,now.toEpochMilli());q.setString(8,user);q.executeUpdate();}return entry(c,list,id);
 }
 private static ShoppingList listRow(ResultSet r,String user,Connection c) throws SQLException {return new ShoppingList(r.getString("id"),r.getString("name"),r.getString("owner_user_id"),Instant.ofEpochMilli(r.getLong("created_at")),r.getLong("item_age_limit_seconds"),r.getLong("version"),user.equals(r.getString("owner_user_id")),schedule(c,r.getString("id")));}
 private static Object schedule(Connection c,String list) throws SQLException {try(var q=c.prepareStatement("SELECT id,enabled,next_due_at FROM schedule_attachments WHERE list_id=? AND detached_at IS NULL")){q.setString(1,list);try(var r=q.executeQuery()){return r.next()?Map.of("id",r.getString(1),"enabled",r.getBoolean(2),"nextDueAt",Instant.ofEpochMilli(r.getLong(3))):null;}}}
 private static Instant instant(ResultSet r,String column) throws SQLException {long value=r.getLong(column);return r.wasNull()?null:Instant.ofEpochMilli(value);}
 private static ShoppingEntry entryRow(ResultSet r) throws SQLException {return new ShoppingEntry(r.getString("id"),r.getString("list_id"),r.getString("display_name"),r.getString("ingredient_id"),r.getString("quantity_decimal"),r.getString("unit"),instant(r,"created_at"),instant(r,"bought_at"),instant(r,"archived_at"),r.getString("created_by"),r.getString("occurrence_id"),r.getString("source_content_revision"),r.getLong("version"));}
}
