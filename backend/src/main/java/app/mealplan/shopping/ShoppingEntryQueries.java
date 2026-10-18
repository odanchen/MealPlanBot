package app.mealplan.shopping;

import app.mealplan.web.ApiErrors;

public final class ShoppingEntryQueries {

    private ShoppingEntryQueries() {}

    public static String order(String sort, boolean groupBought) {
        if (
            !java.util.Set.of("alphabetical", "chronological").contains(sort)
        ) throw ApiErrors.invalid("Choose alphabetical or chronological order");
        return (
            (groupBought ? "(bought_at IS NOT NULL)," : "") +
            (sort.equals("alphabetical") ? "display_name COLLATE NOCASE," : "") +
            "created_at,id"
        );
    }
}
