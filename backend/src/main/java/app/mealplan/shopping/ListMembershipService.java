package app.mealplan.shopping;
import app.mealplan.persistence.*;
import app.mealplan.web.*;
import org.springframework.stereotype.Service;
@Service
public class ListMembershipService {
 private final SqliteDatabase db;private final ListAccessPolicy access;private final ShoppingRepository repository;
 public ListMembershipService(SqliteDatabase db,MigrationRunner migrations,ListAccessPolicy access,ShoppingRepository repository){this.db=db;this.access=access;this.repository=repository;}
 public void leave(String user,String list){db.write(c->{access.member(c,user,list);if(repository.list(c,user,list).owner())throw new ApiErrors.Failure(403,"ownerCannotLeave","The owner can delete the list instead of leaving it",null);
  try(var q=c.prepareStatement("DELETE FROM memberships WHERE list_id=? AND user_id=?")){q.setString(1,list);q.setString(2,user);q.executeUpdate();}return null;
 });}
 public void delete(String user,String list,String match){db.write(c->{access.owner(c,user,list);VersionPreconditions.check(match,repository.list(c,user,list).version());
  try(var q=c.prepareStatement("DELETE FROM shopping_lists WHERE id=?")){q.setString(1,list);q.executeUpdate();}return null;
 });}
}
