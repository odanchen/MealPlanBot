package app.mealplan.shopping;
import java.time.Instant;
public record ShoppingEntry(String id,String listId,String name,String ingredientId,String quantity,String unit,Instant createdAt,Instant boughtAt,Instant archivedAt,String createdBy,String occurrenceId,String sourceContentRevision,long version) {}
