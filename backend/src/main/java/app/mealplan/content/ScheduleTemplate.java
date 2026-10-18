package app.mealplan.content;
import java.util.List;
public record ScheduleTemplate(int schemaVersion,String id,String title,int cycleLength,List<Slot> slots) {
 public record Slot(String recipeId,boolean rest) {}
 public ScheduleTemplate {slots=List.copyOf(slots);}
}
