package app.mealplan.schedule;
import app.mealplan.persistence.*;
import app.mealplan.content.*;
import app.mealplan.domain.schedule.*;
import java.time.*;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class ScheduleGenerationService {
 private final SqliteDatabase db;private final OccurrenceRepository occurrences;private final ContentLoader content;private final Clock clock;private final Object[] locks=new Object[64];
 public ScheduleGenerationService(SqliteDatabase db,MigrationRunner migrations,OccurrenceRepository occurrences,ContentLoader content,Clock clock){this.db=db;this.occurrences=occurrences;this.content=content;this.clock=clock;Arrays.setAll(locks,i->new Object());}
 public void initial(String attachment){var occurrence=db.read(c->occurrences.latest(c,attachment));if(occurrence!=null&&occurrence.kind().equals("initial"))generate(occurrence.id());}
 public void generate(String id){String attachmentId=db.read(c->occurrences.get(c,id).attachmentId());synchronized(locks[Math.floorMod(attachmentId.hashCode(),locks.length)]){db.write(c->{var occurrence=occurrences.get(c,id);if(Set.of("completed","skipped").contains(occurrence.status()))return null;var a=occurrences.attachment(c,occurrence.attachmentId());
  if(a.detachedAt()!=null||!a.enabled()||a.sourceScheduleId()==null||a.configurationVersion()!=occurrence.configurationVersion()){occurrences.finish(c,id,"skipped",null,clock.instant());return null;}
  var source=ScheduleAttachmentService.source(c,a.sourceScheduleId());var snapshot=content.snapshot();ShoppingCoveragePlanner.Plan plan;
  try(var q=c.prepareStatement("UPDATE schedule_occurrences SET content_revision=?,source_schedule_version=?,effective_template_id=?,effective_anchor_date=? WHERE id=?")){q.setString(1,snapshot.revision());q.setLong(2,source.version());q.setString(3,source.templateId());q.setString(4,source.anchorDate().toString());q.setString(5,id);q.executeUpdate();}
  try{plan=new ShoppingCoveragePlanner().plan(snapshot,source.templateId(),source.anchorDate(),occurrence.coverageStartDate(),occurrence.coverageEndDateExclusive(),a.lastCoveredEndDateExclusive());}catch(IllegalArgumentException e){occurrences.finish(c,id,"failed","content_unavailable",clock.instant());return null;}
  // All content and current source/link/coverage state are validated under this transaction before any entry is inserted.
  for(var item:plan.ingredients())try(var q=c.prepareStatement("INSERT INTO shopping_entries(id,list_id,display_name,ingredient_id,quantity_decimal,unit,created_at,occurrence_id,source_content_revision) VALUES(?,?,?,?,?,?,?,?,?)")){q.setString(1,UUID.randomUUID().toString());q.setString(2,a.listId());q.setString(3,snapshot.ingredients().get(item.ingredientId()).name());q.setString(4,item.ingredientId());q.setString(5,item.quantity().decimalText());q.setString(6,item.quantity().unit());q.setLong(7,clock.instant().toEpochMilli());q.setString(8,id);q.setString(9,snapshot.revision());q.executeUpdate();}
  if(plan.from().isBefore(plan.to()))try(var q=c.prepareStatement("UPDATE schedule_attachments SET last_covered_start_date=?,last_covered_end_date_exclusive=? WHERE id=?")){q.setString(1,plan.from().toString());q.setString(2,plan.to().toString());q.setString(3,a.id());q.executeUpdate();}
  occurrences.finish(c,id,"completed",null,clock.instant());return null;
 });}}
 public void due(){var pending=db.read(c->{try(var q=c.prepareStatement("SELECT id FROM schedule_occurrences WHERE status='pending' ORDER BY scheduled_at LIMIT 100");var r=q.executeQuery()){var ids=new ArrayList<String>();while(r.next())ids.add(r.getString(1));return ids;}});for(String id:pending)try{generate(id);}catch(app.mealplan.web.ApiErrors.Failure e){if(e.status!=404)throw e;}var candidates=db.read(c->occurrences.due(c,clock.instant()));for(var candidate:candidates){String id=db.write(c->{var a=occurrences.attachment(c,candidate.id());if(a.detachedAt()!=null||!a.enabled()||a.nextDueAt().isAfter(clock.instant()))return null;var due=a.addition().latestAtOrBefore(clock.instant());if(due==null||due.isBefore(a.nextDueAt()))return null;var next=a.addition().nextAfter(due);var occurrence=occurrences.create(c,a,"recurring",due,a.addition().date(due),a.addition().date(next));try(var q=c.prepareStatement("UPDATE schedule_attachments SET next_due_at=? WHERE id=?")){q.setLong(1,next.toEpochMilli());q.setString(2,a.id());q.executeUpdate();}return occurrence.id();});if(id!=null)generate(id);}}
}
