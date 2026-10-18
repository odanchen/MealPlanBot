package app.mealplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class MealPlanApplication {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("validate-content")) {
            System.exit(
                app.mealplan.cli.ContentValidationCommand.run(
                    java.util.Arrays.copyOfRange(args, 1, args.length)
                )
            );
            return;
        }
        if (args.length > 0 && args[0].equals("preview-meals")) {
            System.exit(
                app.mealplan.cli.MealPreviewCommand.run(
                    java.util.Arrays.copyOfRange(args, 1, args.length)
                )
            );
            return;
        }
        if (args.length > 0 && args[0].equals("preview-shopping")) {
            System.exit(
                app.mealplan.cli.ShoppingPreviewCommand.run(
                    java.util.Arrays.copyOfRange(args, 1, args.length)
                )
            );
            return;
        }
        SpringApplication.run(MealPlanApplication.class, args);
    }
}
