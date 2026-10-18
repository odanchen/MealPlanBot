package app.mealplan.cli;
import app.mealplan.domain.schedule.*;
import java.util.*;
public final class ShoppingPreviewCommand {
 public static int run(String[] args){try{var o=MealPreviewCommand.options(args);var plan=new ShoppingCoveragePlanner().plan(o.content(),o.template(),o.anchor(),o.start(),o.start().plusDays(o.days()),null);System.out.println(new tools.jackson.databind.json.JsonMapper().writeValueAsString(ShoppingCoveragePlanner.view(plan,o.content())));return 0;}catch(Exception e){System.err.println("Invalid shopping preview input or content");return 1;}}
 public static void main(String[] args){System.exit(run(args));}
}
