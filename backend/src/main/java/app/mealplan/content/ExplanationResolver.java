package app.mealplan.content;

import static app.mealplan.content.ContentValidator.*;

import java.util.*;

/** One authoring-resolution path shared by validation and immutable recipe projections. */
public final class ExplanationResolver {

    private ExplanationResolver() {}

    public static Map<String, Object> resolve(
        Map<String, Object> segment,
        ContentSnapshot snapshot
    ) {
        keys(segment, Set.of("term", "label", "explanation"), Set.of());
        var term = segment.containsKey("term")
            ? snapshot.terms().get(string(segment, "term"))
            : null;
        require(
            !segment.containsKey("term") || term != null,
            "Unknown term reference, including when overridden"
        );
        require(
            term != null || (segment.containsKey("label") && segment.containsKey("explanation")),
            "Standalone explanation requires both label and explanation"
        );
        if (segment.containsKey("label")) text(string(segment, "label"));
        if (segment.containsKey("explanation")) text(string(segment, "explanation"));
        var result = new LinkedHashMap<>(segment);
        if (term != null) {
            result.putIfAbsent("label", term.label());
            result.putIfAbsent("explanation", term.definition());
        }
        return Collections.unmodifiableMap(result);
    }
}
