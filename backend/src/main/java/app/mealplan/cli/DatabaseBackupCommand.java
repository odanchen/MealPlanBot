package app.mealplan.cli;

import app.mealplan.operations.ReleaseManifest;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Uses SQLite's online backup equivalent VACUUM INTO, including committed WAL pages. */
public final class DatabaseBackupCommand {

    private DatabaseBackupCommand() {}

    public static int run(String command, String[] args) {
        Path output = null;
        try {
            var o = ReleaseCommand.options(args);
            Path release = Path.of(ReleaseCommand.required(o, "--release")).toAbsolutePath();
            var manifest = ReleaseManifest.read(release);
            ReleaseManifest.verify(
                release,
                manifest.sourceSha(),
                Boolean.parseBoolean(o.getOrDefault("--allow-dirty", "false"))
            );
            output = Path.of(ReleaseCommand.required(o, "--output")).toAbsolutePath();
            if (
                !command.equals("migrate-database") && Files.exists(output)
            ) throw new IllegalArgumentException("Output must not exist");
            if (command.equals("backup-database")) {
                Path source = Path.of(ReleaseCommand.required(o, "--database")).toAbsolutePath();
                if (
                    !Files.isRegularFile(source) || Files.isSymbolicLink(source)
                ) throw new IllegalArgumentException("Database must be an existing regular file");
                Files.createDirectory(output);
                Path snapshot = output.resolve("app.sqlite");
                try (
                    var c = DriverManager.getConnection("jdbc:sqlite:" + source);
                    var q = c.prepareStatement("VACUUM INTO ?")
                ) {
                    q.setString(1, snapshot.toString());
                    q.execute();
                }
                validate(snapshot, manifest.migrationVersion());
                Files.copy(release.resolve("manifest.json"), output.resolve("release.json"));
                Files.writeString(
                    output.resolve("checksums.sha256"),
                    ReleaseManifest.hash(snapshot) +
                        "  app.sqlite\n" +
                        ReleaseManifest.hash(output.resolve("release.json")) +
                        "  release.json\n"
                );
            } else if (command.equals("restore-database")) {
                Path backup = Path.of(ReleaseCommand.required(o, "--backup")).toAbsolutePath();
                var expected = Set.of("app.sqlite", "release.json", "checksums.sha256");
                var actual = new HashSet<String>();
                for (var f : ReleaseManifest.files(backup))
                    actual.add(backup.relativize(f).toString());
                if (!actual.equals(expected)) throw new IllegalArgumentException(
                    "Unexpected backup inventory"
                );
                String checks =
                    ReleaseManifest.hash(backup.resolve("app.sqlite")) +
                    "  app.sqlite\n" +
                    ReleaseManifest.hash(backup.resolve("release.json")) +
                    "  release.json\n";
                if (
                    !Files.readString(backup.resolve("checksums.sha256")).equals(checks) ||
                    !Files.readString(backup.resolve("release.json")).equals(
                        Files.readString(release.resolve("manifest.json"))
                    )
                ) throw new IllegalArgumentException("Backup does not match release or checksums");
                Files.copy(backup.resolve("app.sqlite"), output);
                validate(output, manifest.migrationVersion());
            } else if (command.equals("migrate-database")) {
                // Explicit stopped-application migration, atomically applied by the shared runner.
                try (
                    var db = new app.mealplan.persistence.SqliteDatabase(
                        new app.mealplan.config.RuntimeProperties(output, "test", false, null)
                    )
                ) {
                    new app.mealplan.persistence.MigrationRunner(db);
                }
                validate(output, manifest.migrationVersion());
            } else throw new IllegalArgumentException("Unknown database operation");
            System.out.println("Database operation verified");
            return 0;
        } catch (Exception e) {
            System.err.println("Database operation failed: " + e.getMessage());
            return 1;
        }
    }

    public static void validate(Path database, int version) throws Exception {
        try (
            var c = DriverManager.getConnection("jdbc:sqlite:" + database);
            var s = c.createStatement()
        ) {
            try (var r = s.executeQuery("PRAGMA integrity_check")) {
                if (!r.next() || !r.getString(1).equals("ok")) throw new IllegalArgumentException(
                    "SQLite integrity check failed"
                );
            }
            try (var r = s.executeQuery("PRAGMA foreign_key_check")) {
                if (r.next()) throw new IllegalArgumentException("SQLite foreign-key check failed");
            }
            for (var resource : new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources(
                "classpath*:db/migrations/V*__*.sql"
            )) {
                String name = Objects.requireNonNull(resource.getFilename());
                int migration = Integer.parseInt(name.substring(1, name.indexOf("__")));
                if (migration > version) throw new IllegalArgumentException(
                    "Snapshot application version mismatch"
                );
                String checksum = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(
                        resource.getContentAsByteArray()
                    )
                );
                try (
                    var q = c.prepareStatement(
                        "SELECT checksum FROM schema_migrations WHERE version=? AND name=?"
                    )
                ) {
                    q.setInt(1, migration);
                    q.setString(2, name);
                    try (var r = q.executeQuery()) {
                        if (
                            !r.next() || !checksum.equals(r.getString(1))
                        ) throw new IllegalArgumentException(
                            "Snapshot migration checksum mismatch"
                        );
                    }
                }
            }
            try (var r = s.executeQuery("SELECT max(version) FROM schema_migrations")) {
                if (!r.next() || r.getInt(1) != version) throw new IllegalArgumentException(
                    "Snapshot schema does not match release"
                );
            }
        }
    }
}
