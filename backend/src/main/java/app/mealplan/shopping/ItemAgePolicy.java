package app.mealplan.shopping;
import java.sql.*;
import java.time.*;
import app.mealplan.web.*;
import app.mealplan.persistence.*;
import org.springframework.stereotype.Service;
@Service
public class ItemAgePolicy {
 public record Limit(int count,String unit) {}
 private final SqliteDatabase db;private final ListAccessPolicy access;private final ShoppingRepository repository;private final Clock clock;
 public ItemAgePolicy(SqliteDatabase db,MigrationRunner migrations,ListAccessPolicy access,ShoppingRepository repository,Clock clock){this.db=db;this.access=access;this.repository=repository;this.clock=clock;}
 public static boolean expired(Instant created,Instant now,long seconds){return !now.isBefore(created.plusSeconds(seconds));}
 public static void materialize(Connection c,String list,Instant now) throws SQLException {
  try(var q=c.prepareStatement("UPDATE shopping_entries SET archived_at=?,version=version+1 WHERE list_id=? AND archived_at IS NULL AND created_at<=?-(SELECT item_age_limit_seconds*1000 FROM shopping_lists WHERE id=?)")){q.setLong(1,now.toEpochMilli());q.setString(2,list);q.setLong(3,now.toEpochMilli());q.setString(4,list);q.executeUpdate();}
 }
 public ShoppingList update(String user,String list,Limit limit,String match){if(limit==null||limit.count()<=0||!java.util.Set.of("day","week").contains(limit.unit()))throw ApiErrors.invalid("Choose a positive whole number of days or weeks");long seconds=Math.multiplyExact((long)limit.count(),limit.unit().equals("week")?604800:86400);
  return db.write(c->{access.owner(c,user,list);VersionPreconditions.check(match,repository.list(c,user,list).version());materialize(c,list,clock.instant());try(var q=c.prepareStatement("UPDATE shopping_lists SET item_age_limit_seconds=?,version=version+1 WHERE id=?")){q.setLong(1,seconds);q.setString(2,list);q.executeUpdate();}materialize(c,list,clock.instant());return repository.list(c,user,list);});
 }
}
