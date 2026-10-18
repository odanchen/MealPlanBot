package app.mealplan.schedule;
import app.mealplan.content.*;
import app.mealplan.domain.schedule.*;
import app.mealplan.web.ApiErrors;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public final class UserScheduleService {
 private final UserScheduleRepository repository;private final ContentLoader content;private final Clock clock;
 public UserScheduleService(UserScheduleRepository repository,ContentLoader content,Clock clock){this.repository=repository;this.content=content;this.clock=clock;}
 public List<ConfiguredUserSchedule> list(String owner){return repository.list(owner);}
 public ConfiguredUserSchedule get(String owner,String id){return repository.get(owner,id);}
 public ConfiguredUserSchedule create(String owner,String template,LocalDate anchor){if(anchor==null)throw ApiErrors.invalid("Choose an anchor date");if(template==null||!content.snapshot().templates().containsKey(template))throw ApiErrors.invalid("Choose an available schedule template");return repository.create(owner,template,anchor,clock.instant());}
 public ConfiguredUserSchedule update(String owner,String id,LocalDate anchor,String match){if(anchor==null)throw ApiErrors.invalid("Choose an anchor date");return repository.update(owner,id,anchor,match,clock.instant());}
 public record DatedMeals(String revision,String timeZone,List<MealDateResolver.Meal> meals) {}
 public DatedMeals meals(String owner,String id,LocalDate from,LocalDate to,String zone){var config=get(owner,id);QueryRangePolicy.validate(from,to,zone,clock);var snapshot=content.snapshot();var template=snapshot.templates().get(config.templateId());if(template==null)throw ApiErrors.invalid("Schedule template is unavailable");var resolver=new MealDateResolver();return new DatedMeals(snapshot.revision(),zone,from.datesUntil(to).map(date->resolver.resolve(template,config.anchorDate(),date)).toList());}
}
