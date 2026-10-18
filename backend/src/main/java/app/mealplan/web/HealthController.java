package app.mealplan.web;
import app.mealplan.content.*;
import app.mealplan.persistence.*;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
@RestController public final class HealthController {
 private final ContentLoader content;private final SqliteDatabase db;private final String commit;
 public HealthController(ContentLoader content,SqliteDatabase db,MigrationRunner migrations,Environment env){this.content=content;this.db=db;this.commit=env.getProperty("mealplan.commit","development");}
 @GetMapping("/health") public ResponseEntity<?> health(){try{db.read(c->{try(var s=c.createStatement();var r=s.executeQuery("SELECT max(version) FROM schema_migrations")){if(!r.next()||r.getInt(1)<2)throw new IllegalStateException();return null;}});return ResponseEntity.ok().body(Map.of("status","ready","revision",content.snapshot().revision(),"commit",commit));}catch(RuntimeException e){return ResponseEntity.status(503).body(Map.of("status","unavailable","commit",commit));}}
}
