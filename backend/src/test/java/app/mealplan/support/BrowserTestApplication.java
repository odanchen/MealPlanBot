package app.mealplan.support;

import app.mealplan.MealPlanApplication;
import app.mealplan.config.RuntimeProperties;
import app.mealplan.identity.IdentityService;
import app.mealplan.persistence.SqliteDatabase;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.InetAddress;
import java.time.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/** Test classpath only: no identity selector or test endpoints enter the Boot JAR. */
public final class BrowserTestApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(MealPlanApplication.class, Fixtures.class).run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Fixtures {

        @Bean
        @Primary
        MutableClock browserClock() {
            return new MutableClock(Instant.now());
        }

        @Bean
        FilterRegistrationBean<Filter> browserIdentities(
            IdentityService identities,
            RuntimeProperties runtime
        ) {
            if (!runtime.local() || !runtime.fixtureAuth()) throw new IllegalStateException(
                "Browser fixtures require isolated local mode"
            );
            Filter filter = new Filter() {
                @Override
                public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                    throws IOException, ServletException {
                    var request = (HttpServletRequest) req;
                    String subject = request.getHeader("X-Test-Principal");
                    if (subject != null) {
                        if (
                            !InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress() ||
                            !subject.matches("[a-zA-Z0-9-]{1,100}")
                        ) throw new ServletException("Invalid browser fixture");
                        var user = identities.resolve(
                            "https://accounts.google.com",
                            "browser-" + subject,
                            subject,
                            subject + "@example.test"
                        );
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(SqliteTestSupport.principal(user.id()));
                        request
                            .getSession()
                            .setAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                context
                            );
                    }
                    chain.doFilter(req, res);
                }
            };
            var registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(-101);
            return registration;
        }
    }

    @RestController
    @Profile("browser-test")
    public static class FixtureController {

        private final MutableClock clock;
        private final SqliteDatabase db;
        private final app.mealplan.schedule.ScheduleGenerationService generation;

        FixtureController(
            MutableClock clock,
            SqliteDatabase db,
            app.mealplan.schedule.ScheduleGenerationService generation
        ) {
            this.clock = clock;
            this.db = db;
            this.generation = generation;
        }

        @PostMapping("/__test/clock")
        public void setClock(@RequestParam String instant) {
            clock.set(Instant.parse(instant));
        }

        @PostMapping("/__test/run-due")
        public void due() {
            generation.due();
        }

        @PostMapping("/__test/sources/{id}/template")
        public void template(@PathVariable String id, @RequestParam String templateId) {
            db.write(c -> {
                try (
                    var q = c.prepareStatement("UPDATE user_schedules SET template_id=? WHERE id=?")
                ) {
                    q.setString(1, templateId);
                    q.setString(2, id);
                    q.executeUpdate();
                }
                return null;
            });
        }

        @PostMapping("/__test/advance")
        public void advance(@RequestParam long hours) {
            clock.advance(Duration.ofHours(hours));
        }

        @PostMapping("/__test/entries/{id}/archive")
        public void archive(@PathVariable String id) {
            db.write(c -> {
                try (
                    var q = c.prepareStatement(
                        "UPDATE shopping_entries SET archived_at=?,version=version+1 WHERE id=?"
                    )
                ) {
                    q.setLong(1, clock.instant().toEpochMilli());
                    q.setString(2, id);
                    q.executeUpdate();
                }
                return null;
            });
        }
    }
}
