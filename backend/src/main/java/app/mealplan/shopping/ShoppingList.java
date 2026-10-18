package app.mealplan.shopping;
import java.time.Instant;
public record ShoppingList(String id,String name,String ownerUserId,Instant createdAt,long itemAgeLimitSeconds,long version,boolean owner,Object schedule) {}
