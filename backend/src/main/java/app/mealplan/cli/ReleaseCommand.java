package app.mealplan.cli;

import app.mealplan.content.ContentLoader;
import app.mealplan.operations.ReleaseManifest;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

public final class ReleaseCommand {

    private ReleaseCommand() {}

    public static Map<String, String> options(String[] args) {
        var result = new HashMap<String, String>();
        if (args.length % 2 != 0) throw new IllegalArgumentException(
            "Options require --name value pairs"
        );
        for (int i = 0; i < args.length; i += 2) if (
            !args[i].startsWith("--") || result.put(args[i], args[i + 1]) != null
        ) throw new IllegalArgumentException("Invalid or duplicate option");
        return result;
    }

    public static String required(Map<String, String> options, String key) {
        var value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    public static int run(String command, String[] args) {
        Path stage = null;
        try {
            var o = options(args);
            if (command.equals("verify-release")) {
                var m = ReleaseManifest.verify(
                    Path.of(required(o, "--release")).toAbsolutePath(),
                    required(o, "--commit"),
                    Boolean.parseBoolean(o.getOrDefault("--allow-dirty", "false"))
                );
                System.out.println("Verified release " + m.sourceSha());
                return 0;
            }
            var jar = Path.of(required(o, "--jar")).toAbsolutePath();
            var content = Path.of(required(o, "--content")).toAbsolutePath();
            var root = Path.of(required(o, "--root")).toAbsolutePath();
            var output = Path.of(required(o, "--output")).toAbsolutePath();
            var sha = required(o, "--commit");
            if (!sha.matches("[0-9a-f]{40}")) throw new IllegalArgumentException(
                "A full source SHA is required"
            );
            Files.createDirectories(output.getParent());
            stage = Files.createTempDirectory(output.getParent(), "release-");
            Files.copy(jar, stage.resolve("app.jar"));
            for (var file : ReleaseManifest.files(content)) {
                var relative = content.relativize(file);
                if (file.getFileName().toString().equals(".DS_Store")) continue;
                if (
                    !relative.toString().matches(".*\\.(yaml|json|png|jpg|jpeg|webp|gif|avif)$")
                ) throw new IllegalArgumentException("Unexpected content file: " + relative);
                var destination = stage.resolve("content").resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination);
            }
            for (var name : List.of("backup.sh", "restore.sh")) {
                var script = root.resolve("scripts").resolve(name);
                if (Files.exists(script)) {
                    Files.createDirectories(stage.resolve("scripts"));
                    Files.copy(script, stage.resolve("scripts").resolve(name));
                }
            }
            var sums = new TreeMap<String, String>();
            for (var file : ReleaseManifest.files(stage))
                sums.put(
                    stage.relativize(file).toString().replace('\\', '/'),
                    ReleaseManifest.hash(file)
                );
            int migration;
            try (var zip = new ZipFile(jar.toFile())) {
                migration = zip
                    .stream()
                    .map(ZipEntry::getName)
                    .filter(n -> n.matches("BOOT-INF/classes/db/migrations/V[0-9]+__.*\\.sql"))
                    .mapToInt(n ->
                        Integer.parseInt(n.substring(n.lastIndexOf('/') + 2, n.indexOf("__")))
                    )
                    .max()
                    .orElseThrow();
            }
            var m = new ReleaseManifest(
                1,
                sha,
                Boolean.parseBoolean(o.getOrDefault("--dirty", "false")),
                ContentLoader.load(stage.resolve("content")).revision(),
                25,
                migration,
                sums
            );
            Files.writeString(
                stage.resolve("manifest.json"),
                ReleaseManifest.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(m) + "\n"
            );
            var check = new StringBuilder();
            for (var file : ReleaseManifest.files(stage))
                check
                    .append(ReleaseManifest.hash(file))
                    .append("  ")
                    .append(stage.relativize(file).toString().replace('\\', '/'))
                    .append('\n');
            Files.writeString(stage.resolve("checksums.sha256"), check);
            ReleaseManifest.verify(stage, sha, true);
            try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
                for (var file : ReleaseManifest.files(stage)) {
                    var entry = new ZipEntry(stage.relativize(file).toString().replace('\\', '/'));
                    entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
                    zip.putNextEntry(entry);
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            System.out.println(output);
            return 0;
        } catch (Exception e) {
            System.err.println("Release operation failed: " + e.getMessage());
            return 1;
        } finally {
            if (stage != null) try (var paths = Files.walk(stage)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(path);
            } catch (Exception ignored) {
                /* Keep original failure. */
            }
        }
    }
}
