package app.mealplan.identity;
import app.mealplan.persistence.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public final class IdentityService {
 private final SqliteDatabase db; private final Clock clock;
 public IdentityService(SqliteDatabase db,Clock clock,MigrationRunner migrations) { this.db=db;this.clock=clock; }
 public UserAccount resolve(String issuer,String subject,String displayName,String email) {
  if(!Set.of("https://accounts.google.com","accounts.google.com").contains(issuer) || subject==null || subject.isBlank()) throw new IllegalArgumentException("Validated Google identity required");
  return resolveIdentity("https://accounts.google.com",subject,displayName,email,null);
 }
 UserAccount seedFixture(String id) { return resolveIdentity("urn:mealplan:local","fixture","Local cook",null,id); }
 private UserAccount resolveIdentity(String issuer,String subject,String name,String email,String fixedId) {
  return db.write(c -> {
   String id=null;
   try(var s=c.prepareStatement("SELECT user_id FROM external_identities WHERE issuer=? AND subject=?")) {
    s.setString(1,issuer);s.setString(2,subject);try(var r=s.executeQuery()) {if(r.next()) id=r.getString(1);}
   }
   long now=clock.millis();
   if(id==null) {
    id=fixedId==null ? UUID.randomUUID().toString():fixedId;
    try(var s=c.prepareStatement("INSERT INTO users(id,display_name,current_email,created_at,last_login_at) VALUES(?,?,?,?,?)")) {s.setString(1,id);s.setString(2,name);s.setString(3,email);s.setLong(4,now);s.setLong(5,now);s.executeUpdate();}
    try(var s=c.prepareStatement("INSERT INTO external_identities(issuer,subject,user_id,created_at,last_login_at) VALUES(?,?,?,?,?)")) {s.setString(1,issuer);s.setString(2,subject);s.setString(3,id);s.setLong(4,now);s.setLong(5,now);s.executeUpdate();}
   } else {
    try(var s=c.prepareStatement("UPDATE users SET display_name=?,current_email=?,last_login_at=? WHERE id=?")) {s.setString(1,name);s.setString(2,email);s.setLong(3,now);s.setString(4,id);s.executeUpdate();}
    try(var s=c.prepareStatement("UPDATE external_identities SET last_login_at=? WHERE issuer=? AND subject=?")) {s.setLong(1,now);s.setString(2,issuer);s.setString(3,subject);s.executeUpdate();}
   }
   return find(c,id).orElseThrow();
  });
 }
 public Optional<UserAccount> find(String id) { return db.read(c -> find(c,id)); }
 private Optional<UserAccount> find(Connection c,String id) throws SQLException {
  try(var s=c.prepareStatement("SELECT id,display_name,current_email,created_at,last_login_at FROM users WHERE id=?")) {
   s.setString(1,id);try(var r=s.executeQuery()) {if(!r.next()) return Optional.empty(); return Optional.of(new UserAccount(r.getString(1),r.getString(2),r.getString(3),Instant.ofEpochMilli(r.getLong(4)),Instant.ofEpochMilli(r.getLong(5))));}
  }
 }
}
