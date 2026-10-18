package app.mealplan.content;

import static app.mealplan.content.ContentValidator.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.snakeyaml.engine.v2.api.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class ContentLoader {

    private final Path root;
    private final AtomicReference<ContentSnapshot> accepted = new AtomicReference<>();

    public ContentLoader(Environment env) throws java.io.IOException {
        this.root = Path.of(env.getProperty("mealplan.content-path", defaultRoot().toString()))
            .toAbsolutePath()
            .normalize();
        reload();
    }

    public static Path defaultRoot() {
        return (Files.isDirectory(Path.of("content")) ? Path.of("content") : Path.of("../content"))
            .toAbsolutePath()
            .normalize();
    }

    public ContentSnapshot snapshot() {
        return accepted.get();
    }

    /** An invalid candidate never replaces the currently accepted whole revision. */
    public synchronized ContentSnapshot reload() throws java.io.IOException {
        var candidate = load(root);
        accepted.set(candidate);
        return candidate;
    }

    public static ContentSnapshot load(Path supplied) throws java.io.IOException {
        Path root = supplied.toAbsolutePath().normalize();
        require(!Files.isSymbolicLink(root), "Content root cannot be symlink");
        String initialRevision = revision(root);
        var recipes = new LinkedHashMap<String, RecipeDocument>();
        var templates = new LinkedHashMap<String, ScheduleTemplate>();
        var ingredientYaml = read(root.resolve("ingredients.yaml"));
        keys(
            ingredientYaml,
            Set.of("schemaVersion", "categories", "ingredients"),
            Set.of("schemaVersion", "categories", "ingredients")
        );
        version(ingredientYaml);
        var categories = list(ingredientYaml.get("categories"))
            .stream()
            .map(v -> {
                require(v instanceof String, "Category must be string");
                return (String) v;
            })
            .toList();
        require(new HashSet<>(categories).size() == categories.size(), "Duplicate category");
        var ingredients = new LinkedHashMap<String, ContentSnapshot.Ingredient>();
        map(ingredientYaml.get("ingredients")).forEach((id, value) -> {
            var i = map(value);
            keys(i, Set.of("name", "aliases", "category"), Set.of("name", "aliases", "category"));
            ingredients.put(
                id,
                new ContentSnapshot.Ingredient(
                    string(i, "name"),
                    list(i.get("aliases"))
                        .stream()
                        .map(v -> {
                            require(v instanceof String, "Alias must be string");
                            return (String) v;
                        })
                        .toList(),
                    string(i, "category")
                )
            );
        });
        var glossary = read(root.resolve("glossary.yaml"));
        keys(glossary, Set.of("schemaVersion", "terms"), Set.of("schemaVersion", "terms"));
        version(glossary);
        var terms = new LinkedHashMap<String, ContentSnapshot.Term>();
        map(glossary.get("terms")).forEach((id, value) -> {
            var t = map(value);
            keys(t, Set.of("label", "definition"), Set.of("label", "definition"));
            terms.put(id, new ContentSnapshot.Term(string(t, "label"), string(t, "definition")));
        });
        try (var paths = Files.list(root.resolve("recipes"))) {
            for (var directory : paths.sorted().toList()) {
                require(!Files.isSymbolicLink(directory), "Recipe symlink forbidden");
                if (!Files.isDirectory(directory)) continue;
                var r = read(directory.resolve("recipe.yaml"));
                keys(
                    r,
                    Set.of(
                        "schemaVersion",
                        "id",
                        "title",
                        "servings",
                        "timing",
                        "preparationGuidance",
                        "images",
                        "ingredients",
                        "sections"
                    ),
                    Set.of(
                        "schemaVersion",
                        "id",
                        "title",
                        "servings",
                        "timing",
                        "preparationGuidance",
                        "ingredients",
                        "sections"
                    )
                );
                version(r);
                var serving = map(r.get("servings"));
                keys(serving, Set.of("estimate", "note"), Set.of("estimate", "note"));
                var timing = map(r.get("timing"));
                keys(timing, Set.of("prepMinutes", "cookMinutes", "guidance"), Set.of());
                var images = new ArrayList<RecipeDocument.Image>();
                for (var value : list(r.getOrDefault("images", List.of()))) {
                    var i = map(value);
                    keys(i, Set.of("path", "alt"), Set.of("path", "alt"));
                    images.add(new RecipeDocument.Image(string(i, "path"), string(i, "alt")));
                }
                var recipeIngredients = new ArrayList<RecipeDocument.Ingredient>();
                for (var value : list(r.get("ingredients"))) {
                    var i = map(value);
                    keys(
                        i,
                        Set.of("ingredientId", "quantity", "unit", "optional", "note"),
                        Set.of("ingredientId", "quantity", "unit")
                    );
                    recipeIngredients.add(
                        new RecipeDocument.Ingredient(
                            string(i, "ingredientId"),
                            string(i, "quantity"),
                            string(i, "unit"),
                            bool(i, "optional", false),
                            optionalString(i, "note", "")
                        )
                    );
                }
                var sections = new ArrayList<RecipeDocument.Section>();
                for (var value : list(r.get("sections"))) {
                    var section = map(value);
                    keys(section, Set.of("id", "title", "steps"), Set.of("id", "title", "steps"));
                    var steps = new ArrayList<RecipeDocument.Step>();
                    for (var item : list(section.get("steps"))) {
                        var step = map(item);
                        keys(step, Set.of("id", "content"), Set.of("id", "content"));
                        steps.add(
                            new RecipeDocument.Step(
                                string(step, "id"),
                                list(step.get("content")).stream().map(ContentLoader::map).toList()
                            )
                        );
                    }
                    sections.add(
                        new RecipeDocument.Section(
                            string(section, "id"),
                            string(section, "title"),
                            steps
                        )
                    );
                }
                String id = string(r, "id");
                require(
                    directory.getFileName().toString().equals(id),
                    "Recipe directory must match slug"
                );
                require(
                    recipes.put(
                        id,
                        new RecipeDocument(
                            1,
                            id,
                            string(r, "title"),
                            new RecipeDocument.Servings(
                                integer(serving, "estimate"),
                                string(serving, "note")
                            ),
                            new RecipeDocument.Timing(
                                optionalInteger(timing, "prepMinutes"),
                                optionalInteger(timing, "cookMinutes"),
                                optionalString(timing, "guidance", null)
                            ),
                            string(r, "preparationGuidance"),
                            images,
                            recipeIngredients,
                            sections
                        )
                    ) == null,
                    "Duplicate recipe"
                );
            }
        }
        try (var paths = Files.list(root.resolve("schedules"))) {
            for (var path : paths.sorted().toList()) {
                var t = read(path);
                keys(
                    t,
                    Set.of("schemaVersion", "id", "title", "cycleLength", "slots"),
                    Set.of("schemaVersion", "id", "title", "cycleLength", "slots")
                );
                version(t);
                var slots = new ArrayList<ScheduleTemplate.Slot>();
                for (var value : list(t.get("slots"))) {
                    var slot = map(value);
                    keys(slot, Set.of("recipeId", "rest"), Set.of());
                    require(slot.size() == 1, "Slot must be recipe or rest");
                    slots.add(
                        new ScheduleTemplate.Slot(
                            optionalString(slot, "recipeId", null),
                            bool(slot, "rest", false)
                        )
                    );
                }
                var id = string(t, "id");
                require(
                    path
                        .getFileName()
                        .toString()
                        .equals(id + ".yaml"),
                    "Template filename must match slug"
                );
                require(
                    templates.put(
                        id,
                        new ScheduleTemplate(
                            1,
                            id,
                            string(t, "title"),
                            integer(t, "cycleLength"),
                            slots
                        )
                    ) == null,
                    "Duplicate template"
                );
            }
        }
        String finalRevision = revision(root);
        require(
            initialRevision.equals(finalRevision),
            "Content changed during validation; retry with a complete revision"
        );
        var snapshot = new ContentSnapshot(
            finalRevision,
            root,
            recipes,
            templates,
            categories,
            ingredients,
            terms
        );
        ContentValidator.validate(snapshot);
        return snapshot;
    }

    private static String revision(Path root) throws java.io.IOException {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted().toList()) {
                    require(!Files.isSymbolicLink(path), "Content symlink forbidden");
                    if (!Files.isRegularFile(path)) continue;
                    hash.update(root.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
                    hash.update(Files.readAllBytes(path));
                }
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> read(Path path) throws java.io.IOException {
        require(!Files.isSymbolicLink(path), "YAML symlink forbidden");
        var settings = LoadSettings.builder()
            .setAllowDuplicateKeys(false)
            .setMaxAliasesForCollections(0)
            .setCodePointLimit(1_000_000)
            .build();
        try {
            var parsed = map(new Load(settings).loadFromString(Files.readString(path)));
            plainData(parsed);
            return parsed;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid content file: " + path.getFileName(), e);
        }
    }

    private static void plainData(Object value) {
        if (value instanceof String text) require(
            !text.matches("(?s).*<[/!a-zA-Z][^>]*>.*"),
            "HTML is not content"
        );
        else if (value instanceof Map<?, ?> map) map.values().forEach(ContentLoader::plainData);
        else if (value instanceof List<?> list) list.forEach(ContentLoader::plainData);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        require(value instanceof Map<?, ?>, "Expected mapping");
        var m = (Map<?, ?>) value;
        require(
            m.keySet().stream().allMatch(String.class::isInstance),
            "Mapping keys must be strings"
        );
        return (Map<String, Object>) m;
    }

    private static List<?> list(Object value) {
        require(value instanceof List<?>, "Expected list");
        return (List<?>) value;
    }

    private static int integer(Map<String, Object> m, String key) {
        var v = m.get(key);
        require(v instanceof Integer, "Expected integer: " + key);
        return (Integer) v;
    }

    private static Integer optionalInteger(Map<String, Object> m, String key) {
        return m.containsKey(key) ? integer(m, key) : null;
    }

    private static String optionalString(Map<String, Object> m, String key, String fallback) {
        return m.containsKey(key) ? string(m, key) : fallback;
    }

    private static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        if (!m.containsKey(key)) return fallback;
        require(m.get(key) instanceof Boolean, "Expected boolean");
        return (Boolean) m.get(key);
    }

    private static void version(Map<String, Object> m) {
        require(integer(m, "schemaVersion") == 1, "Unsupported schema version");
    }
}
