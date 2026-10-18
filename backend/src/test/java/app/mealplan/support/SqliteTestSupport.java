package app.mealplan.support;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
public final class SqliteTestSupport {
 private SqliteTestSupport() {}
 public static Connection open(Path file) throws SQLException {
  var c = DriverManager.getConnection("jdbc:sqlite:" + file);
  try (var s = c.createStatement()) { s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA synchronous=FULL"); s.execute("PRAGMA busy_timeout=3000"); }
  return c;
 }
 public static Authentication principal(String userId) { return new UsernamePasswordAuthenticationToken(userId, null, List.of()); }
}
