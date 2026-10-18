package app.mealplan.shopping;

import java.time.Instant;

/** Public owner metadata deliberately excludes even the stored token hash. */
public record Invitation(
    String id,
    String listId,
    String createdBy,
    Instant createdAt,
    Instant expiresAt,
    Instant withdrawnAt
) {}
