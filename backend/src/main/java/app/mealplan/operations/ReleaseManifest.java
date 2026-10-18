package app.mealplan.operations;

import app.mealplan.content.ContentLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import tools.jackson.databind.json.JsonMapper;

/** Complete release identity and inventory, shared by packaging and recovery tools. */
public record ReleaseManifest(
    int schemaVersion,
    String sourceSha,
    boolean sourceDirty,
    String contentRevision,
    int javaTarget,
    int migrationVersion,
    Map<String, String> checksums
) {
    public static final JsonMapper JSON = new JsonMapper();

    public static String hash(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int size;
            while ((size = input.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static List<Path> files(Path root) throws Exception {
        try (var walk = Files.walk(root)) {
            var all = walk.sorted().toList();
            for (var file : all)
                if (Files.isSymbolicLink(file)) throw new IllegalArgumentException(
                    "Symlinks are not allowed in a release"
                );
            return all.stream().filter(Files::isRegularFile).toList();
        }
    }

    public static ReleaseManifest read(Path root) throws Exception {
        return JSON.readValue(
            Files.readString(root.resolve("manifest.json")),
            ReleaseManifest.class
        );
    }

    public static ReleaseManifest verify(Path root, String expectedSha, boolean allowDirty)
        throws Exception {
        var manifest = read(root);
        if (
            manifest.schemaVersion != 1 ||
            manifest.javaTarget != 25 ||
            !manifest.sourceSha.matches("[0-9a-f]{40}") ||
            !manifest.sourceSha.equals(expectedSha) ||
            (!allowDirty && manifest.sourceDirty)
        ) throw new IllegalArgumentException(
            "Release identity does not match the requested clean source commit"
        );
        var actual = new TreeSet<String>();
        for (var file : files(root))
            actual.add(root.relativize(file).toString().replace('\\', '/'));
        var expected = new TreeSet<>(manifest.checksums.keySet());
        expected.add("manifest.json");
        expected.add("checksums.sha256");
        if (!actual.equals(expected)) throw new IllegalArgumentException(
            "Release file inventory differs from manifest"
        );
        var inventory = new TreeMap<String, String>();
        for (String line : Files.readAllLines(root.resolve("checksums.sha256"))) {
            if (!line.matches("[0-9a-f]{64}  [A-Za-z0-9_./-]+")) throw new IllegalArgumentException(
                "Invalid checksum inventory"
            );
            String name = line.substring(66);
            if (
                inventory.put(name, line.substring(0, 64)) != null
            ) throw new IllegalArgumentException("Duplicate checksum entry");
        }
        var expectedInventory = new TreeMap<>(manifest.checksums);
        expectedInventory.put("manifest.json", hash(root.resolve("manifest.json")));
        if (!inventory.equals(expectedInventory)) throw new IllegalArgumentException(
            "Checksum inventory differs from manifest"
        );
        if (
            !manifest.checksums.containsKey("app.jar") ||
            manifest.migrationVersion < 1 ||
            manifest.checksums
                .keySet()
                .stream()
                .anyMatch(
                    n ->
                        !(
                            n.equals("app.jar") ||
                            n.startsWith("content/") ||
                            n.equals("scripts/backup.sh") ||
                            n.equals("scripts/restore.sh")
                        )
                )
        ) throw new IllegalArgumentException("Invalid release payload");
        for (var item : manifest.checksums.entrySet()) {
            var path = root.resolve(item.getKey()).normalize();
            if (
                !path.startsWith(root.normalize()) || !hash(path).equals(item.getValue())
            ) throw new IllegalArgumentException("Release checksum mismatch");
        }
        if (
            !ContentLoader.load(root.resolve("content")).revision().equals(manifest.contentRevision)
        ) throw new IllegalArgumentException("Content revision mismatch");
        try (var jar = new ZipFile(root.resolve("app.jar").toFile())) {
            var manifestEntry = jar.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry == null) throw new IllegalArgumentException(
                "Expected executable Boot JAR manifest"
            );
            try (var input = jar.getInputStream(manifestEntry)) {
                var attributes = new java.util.jar.Manifest(input).getMainAttributes();
                if (
                    !"org.springframework.boot.loader.launch.JarLauncher".equals(
                        attributes.getValue("Main-Class")
                    ) ||
                    !"app.mealplan.MealPlanApplication".equals(attributes.getValue("Start-Class"))
                ) throw new IllegalArgumentException("Expected executable MealPlan Boot JAR");
            }

            int migration = jar
                .stream()
                .map(ZipEntry::getName)
                .filter(n -> n.matches("BOOT-INF/classes/db/migrations/V[0-9]+__.*\\.sql"))
                .mapToInt(n ->
                    Integer.parseInt(n.substring(n.lastIndexOf('/') + 2, n.indexOf("__")))
                )
                .max()
                .orElseThrow();
            if (migration != manifest.migrationVersion) throw new IllegalArgumentException(
                "Migration version mismatch"
            );
            if (
                jar.getEntry("BOOT-INF/classes/static/index.html") == null ||
                jar.getEntry("org/springframework/boot/loader/launch/JarLauncher.class") == null ||
                jar
                    .stream()
                    .noneMatch(
                        e ->
                            e.getName().startsWith("BOOT-INF/classes/static/assets/") &&
                            e.getName().endsWith(".js")
                    )
            ) throw new IllegalArgumentException(
                "Expected executable Boot JAR with built frontend"
            );
        }
        return manifest;
    }
}
