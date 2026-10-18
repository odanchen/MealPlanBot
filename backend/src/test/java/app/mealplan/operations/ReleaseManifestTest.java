package app.mealplan.operations;

import static org.assertj.core.api.Assertions.*;

import app.mealplan.cli.ReleaseCommand;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseManifestTest {

    @TempDir
    Path dir;

    static final String SHA = "a".repeat(40);

    Path release() throws Exception {
        Path jar = dir.resolve("boot.jar"),
            bundle = dir.resolve("release.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(
                "Manifest-Version: 1.0\nMain-Class: org.springframework.boot.loader.launch.JarLauncher\nStart-Class: app.mealplan.MealPlanApplication\n\n".getBytes()
            );
            zip.closeEntry();

            for (String name : List.of(
                "BOOT-INF/classes/static/index.html",
                "BOOT-INF/classes/static/assets/main.js",
                "org/springframework/boot/loader/launch/JarLauncher.class",
                "BOOT-INF/classes/db/migrations/V005__schema.sql"
            )) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("fixture".getBytes());
                zip.closeEntry();
            }
        }
        assertThat(
            ReleaseCommand.run("package-release", new String[] {
                "--jar",
                jar.toString(),
                "--root",
                dir.toString(),
                "--content",
                Path.of("../content").toAbsolutePath().toString(),
                "--commit",
                SHA,
                "--output",
                bundle.toString(),
            })
        ).isZero();
        Path root = Files.createDirectory(dir.resolve("extracted"));
        try (var zip = new ZipFile(bundle.toFile())) {
            for (var e : Collections.list(zip.entries())) {
                Path file = root.resolve(e.getName());
                Files.createDirectories(file.getParent());
                try (var in = zip.getInputStream(e)) {
                    Files.copy(in, file);
                }
            }
        }
        return root;
    }

    @Test
    void verifiesEveryInventoryIncludingManifestAndRejectsExtraFiles() throws Exception {
        Path root = release();
        assertThat(ReleaseManifest.verify(root, SHA, false).migrationVersion()).isEqualTo(5);
        Path sums = root.resolve("checksums.sha256");
        String original = Files.readString(sums);
        Files.writeString(sums, original.replaceFirst("[a-f0-9]", "z"));
        assertThatThrownBy(() -> ReleaseManifest.verify(root, SHA, false)).isInstanceOf(
            IllegalArgumentException.class
        );
        Files.writeString(sums, original);
        Files.writeString(
            root.resolve("manifest.json"),
            Files.readString(root.resolve("manifest.json")) + " "
        );
        assertThatThrownBy(() -> ReleaseManifest.verify(root, SHA, false)).hasMessageContaining(
            "inventory"
        );
    }

    @Test
    void rejectsTamperedPayloadAndUnexpectedFiles() throws Exception {
        Path root = release();
        Files.writeString(root.resolve("extra"), "unexpected");
        assertThatThrownBy(() -> ReleaseManifest.verify(root, SHA, false)).hasMessageContaining(
            "inventory"
        );
        Files.delete(root.resolve("extra"));
        Files.writeString(root.resolve("app.jar"), "tampered");
        assertThatThrownBy(() -> ReleaseManifest.verify(root, SHA, false)).hasMessageContaining(
            "checksum mismatch"
        );
    }

    @Test
    void snapshotIncludesWalAndRestorePreservesPrivateStateAndDeduplication() throws Exception {
        Path root = release(),
            database = dir.resolve("live.sqlite"),
            backup = dir.resolve("backup"),
            restored = dir.resolve("restored.sqlite");
        try (
            var db = new app.mealplan.persistence.SqliteDatabase(
                new app.mealplan.config.RuntimeProperties(database, "test", false, null)
            )
        ) {
            new app.mealplan.persistence.MigrationRunner(db);
            db.write(c -> {
                try (var q = c.createStatement()) {
                    q.execute(
                        "INSERT INTO users VALUES('00000000-0000-4000-8000-000000000001','Owner',NULL,1,1)"
                    );
                    q.execute(
                        "INSERT INTO shopping_lists(id,name,owner_user_id,created_at) VALUES('list','Household','00000000-0000-4000-8000-000000000001',1)"
                    );
                    q.execute(
                        "INSERT INTO memberships VALUES('list','00000000-0000-4000-8000-000000000001',1)"
                    );
                    q.execute(
                        "INSERT INTO invitations VALUES('invite','list','hashed-token','00000000-0000-4000-8000-000000000001',1,259200001,2)"
                    );
                    q.execute(
                        "INSERT INTO schedule_attachments(id,list_id,detached_at,enabled,interval_unit,interval_count,weekday,local_time,time_zone,recurrence_start_date,next_due_at,created_at) VALUES('attachment','list',2,0,'week',1,6,'09:00','UTC','2026-10-20',1,1)"
                    );
                    q.execute(
                        "INSERT INTO schedule_occurrences(id,attachment_id,kind,scheduled_at,configuration_version,coverage_start_date,coverage_end_date_exclusive,time_zone,status,completed_at) VALUES('occurrence','attachment','initial',1,0,'2026-10-20','2026-10-24','UTC','completed',2)"
                    );
                    q.execute(
                        "INSERT INTO shopping_entries(id,list_id,display_name,created_at,bought_at,archived_at,occurrence_id) VALUES('entry','list','Rice',1,2,3,'occurrence')"
                    );
                }
                return null;
            });
            assertThat(Files.size(Path.of(database + "-wal"))).isPositive();
            assertThat(
                app.mealplan.cli.DatabaseBackupCommand.run("backup-database", new String[] {
                    "--database",
                    database.toString(),
                    "--release",
                    root.toString(),
                    "--output",
                    backup.toString(),
                })
            ).isZero();
        }
        assertThat(
            app.mealplan.cli.DatabaseBackupCommand.run("restore-database", new String[] {
                "--backup",
                backup.toString(),
                "--release",
                root.toString(),
                "--output",
                restored.toString(),
            })
        ).isZero();
        try (
            var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + restored);
            var q = c.createStatement()
        ) {
            for (String table : List.of(
                "memberships",
                "shopping_entries",
                "invitations",
                "schedule_occurrences"
            )) {
                try (var r = q.executeQuery("SELECT count(*) FROM " + table)) {
                    assertThat(r.getInt(1)).isEqualTo(1);
                }
            }
            try (var r = q.executeQuery("SELECT withdrawn_at FROM invitations")) {
                assertThat(r.getInt(1)).isEqualTo(2);
            }
            try (
                var r = q.executeQuery(
                    "SELECT source_schedule_id,enabled FROM schedule_attachments"
                )
            ) {
                assertThat(r.getString(1)).isNull();
                assertThat(r.getInt(2)).isZero();
            }
            q.execute("PRAGMA foreign_keys=ON");
            assertThatThrownBy(() ->
                q.execute(
                    "INSERT INTO schedule_occurrences SELECT 'duplicate',attachment_id,kind,scheduled_at,configuration_version,coverage_start_date,coverage_end_date_exclusive,time_zone,content_revision,source_schedule_version,effective_template_id,effective_anchor_date,status,failure_code,completed_at FROM schedule_occurrences"
                )
            ).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() ->
                q.execute("UPDATE shopping_entries SET occurrence_id='missing'")
            ).isInstanceOf(java.sql.SQLException.class);
        }
        Files.writeString(backup.resolve("app.sqlite"), "corrupt");
        assertThat(
            app.mealplan.cli.DatabaseBackupCommand.run("restore-database", new String[] {
                "--backup",
                backup.toString(),
                "--release",
                root.toString(),
                "--output",
                dir.resolve("rejected.sqlite").toString(),
            })
        ).isEqualTo(1);
    }
}
