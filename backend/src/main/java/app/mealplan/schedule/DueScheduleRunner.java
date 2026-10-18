package app.mealplan.schedule;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "mealplan.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DueScheduleRunner {

    private final ScheduleGenerationService generation;

    public DueScheduleRunner(ScheduleGenerationService generation) {
        this.generation = generation;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 3000)
    public void run() {
        generation.due();
    }
}
