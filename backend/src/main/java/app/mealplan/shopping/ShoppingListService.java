package app.mealplan.shopping;
import app.mealplan.persistence.*;
import app.mealplan.web.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class ShoppingListService {
 private final SqliteDatabase db;private final ShoppingRepository repository;private final ListAccessPolicy access;private final IdempotentOperations operations;private final Clock clock;
 public ShoppingListService(SqliteDatabase db,MigrationRunner migrations,ShoppingRepository repository,ListAccessPolicy access,IdempotentOperations operations,Clock clock){this.db=db;this.repository=repository;this.access=access;this.operations=operations;this.clock=clock;}
 static String name(String value){if(value==null||value.isBlank()||value.length()>200)throw ApiErrors.invalid("Name is required and must be at most 200 characters");return value.strip();}
 public ShoppingList create(String user,String name,String key){String valid=name(name);return db.write(c->operations.execute(c,user,"create-list",key,valid,clock.instant(),ShoppingList.class,connection->repository.createList(connection,user,valid,clock.instant())));}
 public List<ShoppingList> catalogue(String user){return db.read(c->repository.catalogue(c,user));}
 public ShoppingList get(String user,String list){return db.read(c->{access.member(c,user,list);return repository.list(c,user,list);});}
}
