package app.mealplan.persistence;

import app.mealplan.config.RuntimeProperties;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Locale;
import org.sqlite.SQLiteConfig;
import org.springframework.stereotype.Component;

@Component
public final class SqliteDatabase implements AutoCloseable {
    private final HikariDataSource pool;
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }
    public SqliteDatabase(RuntimeProperties properties) throws java.io.IOException {
        Path path = properties.databasePath();
        Files.createDirectories(path.getParent());
        // Reject symlink escapes and storage without local SQLite locking semantics.
        if (properties.local() && !path.getParent().toRealPath().endsWith(Path.of(".local/mealplan"))) throw new IllegalArgumentException("Local state must remain isolated");
        if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Database must not be a symlink");
        String type = Files.getFileStore(path.getParent()).type().toLowerCase(Locale.ROOT);
        if (type.contains("nfs") || type.contains("smb") || type.contains("cifs") || type.contains("sshfs") || type.contains("afs"))
            throw new IllegalArgumentException("SQLite requires local storage");
        var sqlite = new SQLiteConfig();
        sqlite.enforceForeignKeys(true); sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqlite.setSynchronous(SQLiteConfig.SynchronousMode.FULL); sqlite.setBusyTimeout(3000);
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + path); config.setDataSourceProperties(sqlite.toProperties());
        config.setMaximumPoolSize(2); config.setMinimumIdle(2); config.setConnectionTimeout(5000);
        config.setPoolName("mealplan-sqlite");
        pool = new HikariDataSource(config);
    }
    public <T> T read(Work<T> work) {
        try (var connection = pool.getConnection()) { return work.run(connection); }
        catch (SQLException e) { throw new DatabaseFailure(e); }
    }
    public <T> T write(Work<T> work) {
        return read(c -> {
            try (var statement = c.createStatement()) { statement.execute("BEGIN IMMEDIATE"); }
            try {
                T result = work.run(c);
                try (var statement = c.createStatement()) { statement.execute("COMMIT"); }
                return result;
            } catch (SQLException | RuntimeException | Error e) {
                try (var statement = c.createStatement()) { statement.execute("ROLLBACK"); }
                catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        });
    }
    @Override public void close() { pool.close(); }
    public static final class DatabaseFailure extends RuntimeException {
        public DatabaseFailure(SQLException cause) { super("Database operation failed", cause); }
    }
}
