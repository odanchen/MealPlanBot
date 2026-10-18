package app.mealplan.shopping;

import java.time.Instant;

public record Membership(String listId, String userId, Instant joinedAt) {}
