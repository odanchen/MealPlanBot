package app.mealplan.shopping;
import app.mealplan.persistence.*;
import app.mealplan.web.*;
import app.mealplan.content.ContentLoader;
import app.mealplan.domain.measurement.Quantity;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class ShoppingEntryService {
 public record Addition(String name,String ingredientId,String quantity,String unit) {}
 private final SqliteDatabase db;private final ShoppingRepository repository;private final ListAccessPolicy access;private final IdempotentOperations operations;private final Clock clock;private final ContentLoader content;
 public ShoppingEntryService(SqliteDatabase db,MigrationRunner migrations,ShoppingRepository repository,ListAccessPolicy access,IdempotentOperations operations,Clock clock,ContentLoader content){this.db=db;this.repository=repository;this.access=access;this.operations=operations;this.clock=clock;this.content=content;}
 public List<ShoppingEntry> entries(String user,String list,String state,String sort,boolean group){return db.read(c->{access.member(c,user,list);if(!Set.of("active","archived").contains(state)||!Set.of("alphabetical","chronological").contains(sort))throw ApiErrors.invalid("Invalid entry state or sort order");return repository.entries(c,list,state,sort,group);});}
 public ShoppingEntry add(String user,String list,Addition input,String key){return db.write(c->{
  access.member(c,user,list);
  return operations.execute(c,user,"add-entry:"+list,key,input,clock.instant(),ShoppingEntry.class,connection->{
  String name=ShoppingListService.name(input.name());
  if((input.quantity()==null)!=(input.unit()==null))throw ApiErrors.invalid("Quantity and unit must be supplied together");
  String amount=null;if(input.quantity()!=null){try{amount=new Quantity(input.quantity(),input.unit()).decimalText();}catch(IllegalArgumentException e){throw ApiErrors.invalid("Enter a positive decimal quantity and a supported unit; packages require an explicit size");}}
  var snapshot=content.snapshot();String ingredient=input.ingredientId();
  if(ingredient!=null&&!snapshot.ingredients().containsKey(ingredient))throw ApiErrors.invalid("Unknown ingredient");
  // Resolve only exact normalized canonical names or curated aliases; never fuzzy-match products.
  if(ingredient==null){String normalized=normalize(name);for(var item:snapshot.ingredients().entrySet())if(normalize(item.getValue().name()).equals(normalized)||item.getValue().aliases().stream().anyMatch(alias->normalize(alias).equals(normalized))){ingredient=item.getKey();break;}}
  var valid=new Addition(name,ingredient,amount,input.unit());
  return repository.add(connection,user,list,valid.name(),valid.ingredientId(),valid.quantity(),valid.unit(),clock.instant());
  });
 });}
 private static String normalize(String value){return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
 public ShoppingEntry bought(String user,String list,String entry,Boolean bought,String match){return db.write(c->{access.member(c,user,list);var current=repository.entry(c,list,entry);VersionPreconditions.check(match,current.version());if(bought==null)throw ApiErrors.invalid("Bought must be true or false");if(current.archivedAt()!=null)throw ApiErrors.invalid("Archived entries cannot be changed");if((current.boughtAt()!=null)==bought)return current;
  try(var q=c.prepareStatement("UPDATE shopping_entries SET bought_at=?,version=version+1 WHERE id=? AND list_id=?")){if(bought)q.setLong(1,clock.instant().toEpochMilli());else q.setNull(1,java.sql.Types.BIGINT);q.setString(2,entry);q.setString(3,list);q.executeUpdate();}return repository.entry(c,list,entry);
 });}
 public void remove(String user,String list,String entry,String match){db.write(c->{access.member(c,user,list);VersionPreconditions.check(match,repository.entry(c,list,entry).version());try(var q=c.prepareStatement("DELETE FROM shopping_entries WHERE id=? AND list_id=?")){q.setString(1,entry);q.setString(2,list);q.executeUpdate();}return null;});}
}
