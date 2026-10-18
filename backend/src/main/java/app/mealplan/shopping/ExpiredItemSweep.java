package app.mealplan.shopping;
import app.mealplan.persistence.*;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @ConditionalOnProperty(name="mealplan.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class ExpiredItemSweep {
 private final SqliteDatabase db;private final Clock clock;
 public ExpiredItemSweep(SqliteDatabase db,MigrationRunner migrations,Clock clock){this.db=db;this.clock=clock;}
 @Scheduled(fixedDelay=60000,initialDelay=60000) public void run(){db.write(c->{try(var q=c.prepareStatement("UPDATE shopping_entries SET archived_at=?,version=version+1 WHERE id IN (SELECT e.id FROM shopping_entries e JOIN shopping_lists l ON l.id=e.list_id WHERE e.archived_at IS NULL AND e.created_at<=?-l.item_age_limit_seconds*1000 LIMIT 500)")){q.setLong(1,clock.instant().toEpochMilli());q.setLong(2,clock.instant().toEpochMilli());q.executeUpdate();}return null;});}
}
