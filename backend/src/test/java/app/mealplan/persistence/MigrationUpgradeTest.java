package app.mealplan.persistence;
import app.mealplan.config.RuntimeProperties;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class MigrationUpgradeTest {
 @TempDir Path directory;
 @Test void populatedUpgradeRetainsEntriesMembershipsAndEnforcesOccurrenceForeignKey() throws Exception {
  var migrations=new ArrayList<MigrationRunner.Migration>();for(int v=1;v<=5;v++){final int version=v;try(var files=Files.list(Path.of("src/main/resources/db/migrations"))){var path=files.filter(p->p.getFileName().toString().startsWith("V00"+version+"__")).findFirst().orElseThrow();migrations.add(new MigrationRunner.Migration(v,path.getFileName().toString(),Files.readString(path)));}}
  try(var db=new SqliteDatabase(new RuntimeProperties(directory.resolve("app.sqlite"),"test",false,null))){
   MigrationRunner.apply(db,migrations.subList(0,4));db.write(c->{try(var q=c.createStatement()){q.execute("INSERT INTO users VALUES('00000000-0000-4000-8000-000000000001','Cook',NULL,1,1)");q.execute("INSERT INTO shopping_lists VALUES('list','Keep','00000000-0000-4000-8000-000000000001',1,604800,0)");q.execute("INSERT INTO memberships VALUES('list','00000000-0000-4000-8000-000000000001',1)");q.execute("INSERT INTO shopping_entries(id,list_id,display_name,quantity_decimal,unit,created_at,bought_at,archived_at) VALUES('old','list','Preserve','0.3','kg',1,2,3)");}return null;});
   var broken=new ArrayList<>(migrations.subList(0,4));broken.add(new MigrationRunner.Migration(5,"broken",migrations.get(4).sql()+"; INSERT INTO missing VALUES(1)"));assertThatThrownBy(()->MigrationRunner.apply(db,broken)).isInstanceOf(SqliteDatabase.DatabaseFailure.class);
   MigrationRunner.apply(db,migrations);db.read(c->{try(var q=c.createStatement();var r=q.executeQuery("SELECT quantity_decimal,bought_at,archived_at FROM shopping_entries WHERE id='old'")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("0.3");assertThat(r.getLong(2)).isEqualTo(2);assertThat(r.getLong(3)).isEqualTo(3);}try(var q=c.createStatement();var r=q.executeQuery("SELECT count(*) FROM memberships")){assertThat(r.getInt(1)).isEqualTo(1);}return null;});
   assertThatThrownBy(()->db.write(c->{try(var q=c.createStatement()){q.execute("UPDATE shopping_entries SET occurrence_id='missing' WHERE id='old'");}return null;})).isInstanceOf(SqliteDatabase.DatabaseFailure.class);
  }
 }
}
