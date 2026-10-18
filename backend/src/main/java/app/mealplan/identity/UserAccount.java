package app.mealplan.identity;
import java.time.Instant;
public record UserAccount(String id,String displayName,String email,Instant createdAt,Instant lastLoginAt) {}
