package app.mealplan.persistence;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public final class MigrationRunner {

    public record Migration(int version, String name, String sql) {}

    public MigrationRunner(SqliteDatabase database) throws java.io.IOException {
        var migrations = new ArrayList<Migration>();
        for (var resource : new PathMatchingResourcePatternResolver().getResources(
            "classpath*:db/migrations/V*__*.sql"
        )) {
            var name = Objects.requireNonNull(resource.getFilename());
            int version = Integer.parseInt(name.substring(1, name.indexOf("__")));
            migrations.add(
                new Migration(version, name, resource.getContentAsString(StandardCharsets.UTF_8))
            );
        }
        apply(database, migrations);
    }

    public static void apply(SqliteDatabase database, List<Migration> migrations) {
        var ordered = migrations
            .stream()
            .sorted(Comparator.comparingInt(Migration::version))
            .toList();
        if (
            ordered.stream().map(Migration::version).distinct().count() != ordered.size()
        ) throw new IllegalArgumentException("Duplicate migration version");
        database.write(c -> {
            try (var s = c.createStatement()) {
                s.execute(
                    "CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, name TEXT NOT NULL, checksum TEXT NOT NULL)"
                );
            }
            var applied = new HashMap<Integer, String>();
            try (
                var s = c.createStatement();
                var r = s.executeQuery("SELECT version, checksum FROM schema_migrations")
            ) {
                while (r.next()) applied.put(r.getInt(1), r.getString(2));
            }
            for (int version : applied.keySet())
                if (
                    ordered.stream().noneMatch(m -> m.version() == version)
                ) throw new IllegalStateException("Database schema is newer than this application");
            for (var migration : ordered) {
                String hash = checksum(migration.sql());
                if (applied.containsKey(migration.version())) {
                    if (
                        !hash.equals(applied.get(migration.version()))
                    ) throw new IllegalStateException("Applied migration checksum changed");
                    continue;
                }
                if (
                    applied
                        .keySet()
                        .stream()
                        .anyMatch(v -> v > migration.version())
                ) throw new IllegalStateException("Out-of-order migration");
                // Migration files are trusted SQL, with semicolon-delimited statements and no triggers.
                for (String sql : migration.sql().split(";"))
                    if (!sql.isBlank()) try (var s = c.createStatement()) {
                        s.execute(sql);
                    }
                try (
                    var s = c.prepareStatement(
                        "INSERT INTO schema_migrations(version,name,checksum) VALUES(?,?,?)"
                    )
                ) {
                    s.setInt(1, migration.version());
                    s.setString(2, migration.name());
                    s.setString(3, hash);
                    s.executeUpdate();
                }
            }
            return null;
        });
    }

    private static String checksum(String text) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
