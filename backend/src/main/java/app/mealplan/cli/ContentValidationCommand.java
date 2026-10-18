package app.mealplan.cli;
import app.mealplan.content.ContentLoader;
import java.nio.file.Path;
public final class ContentValidationCommand {
 private ContentValidationCommand() {}
 public static int run(String[] args){try{if(args.length>2||args.length==2&&!args[0].equals("--content-path"))throw new IllegalArgumentException("Use validate-content [--content-path PATH]");var snapshot=ContentLoader.load(args.length==2?Path.of(args[1]):ContentLoader.defaultRoot());System.out.println("Valid content: "+snapshot.recipes().size()+" recipes, revision "+snapshot.revision());return 0;}catch(Exception e){System.err.println("Content validation failed: "+e.getMessage());return 1;}}
 public static void main(String[] args){System.exit(run(args));}
}
