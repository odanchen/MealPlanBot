package app.mealplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MealPlanApplication {
    public static void main(String[] args) {
        if(args.length>0&&args[0].equals("validate-content")){System.exit(app.mealplan.cli.ContentValidationCommand.run(java.util.Arrays.copyOfRange(args,1,args.length)));return;}
        SpringApplication.run(MealPlanApplication.class, args);
    }
}
