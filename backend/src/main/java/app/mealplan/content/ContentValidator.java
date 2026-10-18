package app.mealplan.content;

import app.mealplan.domain.measurement.*;
import java.nio.file.*;
import java.util.*;

/** Strict authoring validation independent of HTTP, credentials and database state. */
public final class ContentValidator {

    private ContentValidator() {}

    public static void validate(ContentSnapshot s) throws java.io.IOException {
        require(!s.recipes().isEmpty(), "No recipes");
        require(s.templates().containsKey("three-week"), "Missing supplied three-week template");
        var aliases = new HashMap<String, String>();
        s.ingredients().forEach((id, i) -> {
            slug(id);
            text(i.name());
            require(s.categories().contains(i.category()), "Invalid ingredient category");
            var names = new ArrayList<>(i.aliases());
            names.add(i.name());
            for (String name : names) {
                text(name);
                String normalized = name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
                String old = aliases.putIfAbsent(normalized, id);
                require(old == null || old.equals(id), "Ingredient alias collision");
            }
        });
        s.terms().forEach((id, t) -> {
            slug(id);
            text(t.label());
            text(t.definition());
        });
        for (var r : s.recipes().values()) {
            slug(r.id());
            text(r.title());
            require(r.schemaVersion() == 1, "Unsupported recipe version");
            require(r.servings().estimate() > 0, "Invalid servings");
            text(r.servings().note());
            require(
                r.timing().prepMinutes() == null || r.timing().prepMinutes() >= 0,
                "Invalid prep minutes"
            );
            require(
                r.timing().cookMinutes() == null || r.timing().cookMinutes() >= 0,
                "Invalid cook minutes"
            );
            require(
                r.timing().guidance() != null ||
                    r.timing().prepMinutes() != null ||
                    r.timing().cookMinutes() != null,
                "Missing timing"
            );
            var recipeRoot = s.root().resolve("recipes").resolve(r.id());
            for (var image : r.images()) {
                text(image.alt());
                String path = image.path();
                require(!path.contains("\\") && !Path.of(path).isAbsolute(), "Unsafe image path");
                var resolved = recipeRoot.resolve(path).normalize();
                require(
                    resolved.startsWith(recipeRoot) && !resolved.equals(recipeRoot),
                    "Image path escapes recipe"
                );
                for (
                    var cursor = resolved;
                    cursor != null && cursor.startsWith(recipeRoot);
                    cursor = cursor.getParent()
                ) require(!Files.isSymbolicLink(cursor), "Image symlink forbidden");
                require(
                    Files.isRegularFile(resolved) &&
                        resolved.toRealPath().startsWith(recipeRoot.toRealPath()),
                    "Missing image"
                );
            }
            require(!r.ingredients().isEmpty(), "Missing ingredients");
            for (var i : r.ingredients()) {
                require(s.ingredients().containsKey(i.ingredientId()), "Unknown ingredient");
                require(
                    i.quantity().matches("(?:0|[1-9][0-9]*)(?:[.][0-9]+)?") &&
                        new java.math.BigDecimal(i.quantity()).signum() > 0,
                    "Invalid quantity"
                );
                UnitRegistry.require(i.unit());
            }
            require(!r.sections().isEmpty(), "Missing recipe sections");
            var sectionIds = new HashSet<String>();
            var stepIds = new HashSet<String>();
            for (var section : r.sections()) {
                slug(section.id());
                require(sectionIds.add(section.id()), "Duplicate section ID");
                text(section.title());
                require(!section.steps().isEmpty(), "Missing steps");
                for (var step : section.steps()) {
                    slug(step.id());
                    require(stepIds.add(step.id()), "Duplicate step ID");
                    require(!step.content().isEmpty(), "Missing instruction content");
                    for (var seg : step.content()) {
                        if (seg.containsKey("text")) {
                            keys(seg, Set.of("text"), Set.of("text"));
                            require(seg.get("text") instanceof String, "Text must be string");
                        } else if (seg.containsKey("recipe")) {
                            keys(seg, Set.of("recipe", "label", "section"), Set.of("recipe"));
                            var target = s.recipes().get(string(seg, "recipe"));
                            require(target != null, "Unknown recipe reference");
                            if (seg.containsKey("section")) require(
                                target
                                    .sections()
                                    .stream()
                                    .anyMatch(x -> x.id().equals(string(seg, "section"))),
                                "Unknown section reference"
                            );
                            if (seg.containsKey("label")) text(string(seg, "label"));
                        } else {
                            ExplanationResolver.resolve(seg, s);
                        }
                    }
                }
            }
        }
        for (var t : s.templates().values()) {
            slug(t.id());
            text(t.title());
            require(
                t.schemaVersion() == 1 &&
                    t.cycleLength() > 0 &&
                    t.cycleLength() == t.slots().size(),
                "Invalid template cycle"
            );
            for (var slot : t.slots())
                require(
                    slot.rest()
                        ? slot.recipeId() == null
                        : slot.recipeId() != null && s.recipes().containsKey(slot.recipeId()),
                    "Invalid schedule slot"
                );
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public static void text(String value) {
        require(value != null && !value.isBlank(), "Blank content field");
        require(!value.matches("(?s).*<[/!a-zA-Z][^>]*>.*"), "HTML is not content");
    }

    public static void slug(String value) {
        require(
            value != null && value.matches("[a-zA-Z][a-zA-Z0-9]*(?:-[a-zA-Z0-9]+)*"),
            "Invalid stable slug"
        );
    }

    public static void keys(Map<String, Object> map, Set<String> allowed, Set<String> required) {
        require(
            allowed.containsAll(map.keySet()) && map.keySet().containsAll(required),
            "Unknown or missing content field"
        );
        require(map.values().stream().noneMatch(Objects::isNull), "Null content field");
    }

    public static String string(Map<String, Object> m, String key) {
        var value = m.get(key);
        require(value instanceof String, "Expected string: " + key);
        return (String) value;
    }
}
