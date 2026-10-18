package app.mealplan.domain.schedule;
import app.mealplan.content.ScheduleTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
public final class MealDateResolver {
 public record Meal(LocalDate date,String status,Integer cycleDay,String recipeId) {}
 public Meal resolve(ScheduleTemplate template,LocalDate anchor,LocalDate date){
  long days=ChronoUnit.DAYS.between(anchor,date);if(days<0)return new Meal(date,"not_started",null,null);
  int index=(int)(days%template.cycleLength());var slot=template.slots().get(index);
  return new Meal(date,slot.rest()?"rest":"meal",index+1,slot.recipeId());
 }
}
