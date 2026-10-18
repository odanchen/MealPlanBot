package app.mealplan.cli;
import app.mealplan.content.*;
import app.mealplan.domain.schedule.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
public final class MealPreviewCommand {
 record Options(ContentSnapshot content,String template,LocalDate anchor,LocalDate start,int days) {}
 static Options options(String[] args) throws Exception {var values=new HashMap<String,String>();for(int i=0;i<args.length;i+=2){if(i+1>=args.length||!Set.of("--template-id","--anchor-date","--start-date","--days","--content-path").contains(args[i])||values.put(args[i],args[i+1])!=null)throw new IllegalArgumentException("Expected --template-id ID --anchor-date DATE --start-date DATE --days N [--content-path PATH]");}int days=Integer.parseInt(values.get("--days"));if(days<=0)throw new IllegalArgumentException("Days must be positive");var snapshot=ContentLoader.load(values.containsKey("--content-path")?Path.of(values.get("--content-path")):ContentLoader.defaultRoot());String template=values.get("--template-id");if(!snapshot.templates().containsKey(template))throw new IllegalArgumentException("Unknown template");return new Options(snapshot,template,LocalDate.parse(values.get("--anchor-date")),LocalDate.parse(values.get("--start-date")),days);}
 public static int run(String[] args){try{var o=options(args);var template=o.content().templates().get(o.template());var meals=o.start().datesUntil(o.start().plusDays(o.days())).map(d->new MealDateResolver().resolve(template,o.anchor(),d)).toList();System.out.println(new tools.jackson.databind.json.JsonMapper().writeValueAsString(meals));return 0;}catch(Exception e){System.err.println("Invalid meal preview input or content");return 1;}}
 public static void main(String[] args){System.exit(run(args));}
}
