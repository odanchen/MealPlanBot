package app.mealplan.persistence;
import app.mealplan.config.RuntimeProperties;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class SqliteInfrastructureTest {
 @TempDir Path directory;
 private SqliteDatabase open() throws Exception { return new SqliteDatabase(new RuntimeProperties(directory.resolve("app.sqlite"),"test",false,null)); }
 @Test void everyConnectionEnforcesDurabilityAndForeignKeys() throws Exception {
  try(var db=open()) {
   db.read(a -> db.read(b -> {
    assertThat(a).isNotSameAs(b);
    for(var c:List.of(a,b)) try(var s=c.createStatement()) {
     try(var r=s.executeQuery("PRAGMA foreign_keys")) { assertThat(r.getInt(1)).isEqualTo(1); }
     try(var r=s.executeQuery("PRAGMA journal_mode")) { assertThat(r.getString(1)).isEqualTo("wal"); }
     try(var r=s.executeQuery("PRAGMA synchronous")) { assertThat(r.getInt(1)).isEqualTo(2); }
     try(var r=s.executeQuery("PRAGMA busy_timeout")) { assertThat(r.getInt(1)).isEqualTo(3000); }
    }
    return null;
   }));
  }
 }
 @Test void restartRetainsRowsAndMigrationsAreIdempotentAndChecked() throws Exception {
  var migration=new MigrationRunner.Migration(1,"test","CREATE TABLE sample(id TEXT PRIMARY KEY, amount TEXT NOT NULL, created_at INTEGER NOT NULL)");
  try(var db=open()) {
   MigrationRunner.apply(db,List.of(migration));
   db.write(c -> { try(var s=c.prepareStatement("INSERT INTO sample VALUES(?,?,?)")) { s.setString(1,"row");s.setString(2,"0.3");s.setLong(3,1780000000123L);s.executeUpdate(); } return null; });
  }
  try(var db=open()) {
   MigrationRunner.apply(db,List.of(migration));
   db.read(c -> { try(var s=c.createStatement();var r=s.executeQuery("SELECT amount,typeof(amount),created_at,typeof(created_at) FROM sample")) {
    assertThat(r.getString(1)).isEqualTo("0.3");assertThat(r.getString(2)).isEqualTo("text");assertThat(r.getLong(3)).isEqualTo(1780000000123L);assertThat(r.getString(4)).isEqualTo("integer");
   } return null; });
   assertThatThrownBy(() -> MigrationRunner.apply(db,List.of(new MigrationRunner.Migration(1,"test","SELECT 1")))).isInstanceOf(IllegalStateException.class);
  }
 }
 @Test void failedMigrationAndWriteRollBackCompletely() throws Exception {
  var first=new MigrationRunner.Migration(1,"first","CREATE TABLE original(id TEXT PRIMARY KEY)");
  try(var db=open()) {
   MigrationRunner.apply(db,List.of(first));
   db.write(c -> {try(var statement=c.createStatement()) {statement.execute("INSERT INTO original VALUES('keep')");}return null;});
   assertThatThrownBy(() -> MigrationRunner.apply(db,List.of(first,new MigrationRunner.Migration(2,"broken","CREATE TABLE partial(id TEXT); INSERT INTO missing VALUES(1)")))).isInstanceOf(SqliteDatabase.DatabaseFailure.class);
   db.read(c -> { try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='partial'")) {assertThat(r.getInt(1)).isZero();} return null; });
   assertThatThrownBy(() -> db.write(c -> { try(var s=c.createStatement()) {s.execute("INSERT INTO original VALUES('discard')");} throw new SQLException("failure"); })).isInstanceOf(SqliteDatabase.DatabaseFailure.class);
   db.read(c -> {try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM original")) {assertThat(r.getInt(1)).isEqualTo(1);} return null;});
  }
 }
 @Test void profileIdentitySurvivesRestartAndConcurrentFirstSignInsAreAtomic() throws Exception {
  var clock=new app.mealplan.support.MutableClock(java.time.Instant.parse("2026-10-18T12:00:00Z"));
  String originalId;
  try(var db=open()) {
   var identities=new app.mealplan.identity.IdentityService(db,clock,new MigrationRunner(db));
   originalId=identities.resolve("https://accounts.google.com","persistent-subject","Original","old@example.com").id();
  }
  clock.advance(java.time.Duration.ofDays(1));
  try(var db=open()) {
   var identities=new app.mealplan.identity.IdentityService(db,clock,new MigrationRunner(db));
   var changed=identities.resolve("accounts.google.com","persistent-subject","Updated","shared@example.com");
   assertThat(changed.id()).isEqualTo(originalId);assertThat(changed.email()).isEqualTo("shared@example.com");
   assertThat(changed.createdAt()).isEqualTo(java.time.Instant.parse("2026-10-18T12:00:00Z"));assertThat(changed.lastLoginAt()).isEqualTo(clock.instant());
   var other=identities.resolve("https://accounts.google.com","different-subject","Other","shared@example.com");assertThat(other.id()).isNotEqualTo(originalId);
   try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
    var calls=new java.util.ArrayList<java.util.concurrent.Callable<String>>();
    for(int i=0;i<8;i++) calls.add(() -> identities.resolve("https://accounts.google.com","concurrent-new-subject","Cook","shared@example.com").id());
    var ids=new java.util.HashSet<String>();for(var future:executor.invokeAll(calls)) ids.add(future.get());assertThat(ids).hasSize(1);
   }
   assertThatThrownBy(() -> db.write(c -> {try(var statement=c.createStatement()) {statement.execute("INSERT INTO external_identities VALUES('issuer','subject','nonexistent-user',1,1)");}return null;})).isInstanceOf(SqliteDatabase.DatabaseFailure.class);
  }
 }
}
