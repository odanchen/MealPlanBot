package app.mealplan.shopping;
import app.mealplan.persistence.*;
import java.util.List;
import org.springframework.stereotype.Service;
@Service
public class ArchiveService {
 private final SqliteDatabase db;private final ListAccessPolicy access;private final ShoppingRepository repository;
 public ArchiveService(SqliteDatabase db,MigrationRunner migrations,ListAccessPolicy access,ShoppingRepository repository){this.db=db;this.access=access;this.repository=repository;}
 public List<ShoppingEntry> entries(String user,String list){return db.read(c->{access.member(c,user,list);return repository.entries(c,list,"archived","alphabetical",true);});}
 public void clear(String user,String list){db.write(c->{access.member(c,user,list);try(var q=c.prepareStatement("DELETE FROM shopping_entries WHERE list_id=? AND archived_at IS NOT NULL")){q.setString(1,list);q.executeUpdate();}return null;});}
}
