package app.mealplan.schedule;
import app.mealplan.persistence.*;
import app.mealplan.shopping.ListAccessPolicy;
import app.mealplan.web.ApiErrors;
import org.springframework.stereotype.Service;
@Service
public class ScheduleRetryService {
 private final SqliteDatabase db;private final ListAccessPolicy access;private final OccurrenceRepository occurrences;private final ScheduleGenerationService generation;
 public ScheduleRetryService(SqliteDatabase db,ListAccessPolicy access,OccurrenceRepository occurrences,ScheduleGenerationService generation){this.db=db;this.access=access;this.occurrences=occurrences;this.generation=generation;}
 public void retry(String user,String list,String id){db.read(c->{access.owner(c,user,list);var o=occurrences.get(c,id);if(!occurrences.attachment(c,o.attachmentId()).listId().equals(list))throw ApiErrors.notFound();if(!java.util.Set.of("failed","completed","skipped").contains(o.status()))throw ApiErrors.invalid("Only a failed occurrence can be retried");return null;});generation.generate(id);}
}
