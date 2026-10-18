package app.mealplan.config;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RuntimePropertiesTest {

    @Test
    void refusesFixtureOnPiAndProduction() {
        for (String target : new String[] { "pi", "production" })
            assertThatThrownBy(() ->
                new RuntimeProperties(
                    Path.of("/tmp/app.sqlite"),
                    target,
                    true,
                    URI.create("https://example.com")
                )
            ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresCanonicalHttpsAndIsolatedLocalState() {
        assertThatThrownBy(() ->
            new RuntimeProperties(
                Path.of("/tmp/app.sqlite"),
                "pi",
                false,
                URI.create("http://example.com")
            )
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            new RuntimeProperties(Path.of("/tmp/app.sqlite"), "local", true, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void localProfileCannotOverrideIndependentPiTargetOrPublicBind() {
        var env = new MockEnvironment()
            .withProperty("mealplan.deployment-target", "pi")
            .withProperty("mealplan.fixture-auth", "true")
            .withProperty("mealplan.database-path", ".local/mealplan/app.sqlite");
        env.setActiveProfiles("local");
        assertThatThrownBy(() ->
            new RuntimeProperties.Binding().runtimeProperties(env)
        ).isInstanceOf(IllegalArgumentException.class);
        env.setProperty("mealplan.deployment-target", "local");
        env.setProperty("server.address", "0.0.0.0");
        assertThatThrownBy(() ->
            new RuntimeProperties.Binding().runtimeProperties(env)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void productionCannotRelaxSessionSafety() {
        var env = new MockEnvironment()
            .withProperty("mealplan.deployment-target", "pi")
            .withProperty("mealplan.database-path", "/tmp/app.sqlite")
            .withProperty("mealplan.public-base-url", "https://example.com")
            .withProperty("server.servlet.session.cookie.secure", "false");
        assertThatThrownBy(() ->
            new RuntimeProperties.Binding().runtimeProperties(env)
        ).isInstanceOf(IllegalArgumentException.class);
        env.setProperty("server.servlet.session.cookie.secure", "true");
        env.setProperty("server.servlet.session.persistent", "true");
        assertThatThrownBy(() ->
            new RuntimeProperties.Binding().runtimeProperties(env)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testProfileCannotFallBackToAnUnspecifiedDatabase() {
        var env = new MockEnvironment()
            .withProperty("mealplan.deployment-target", "test")
            .withProperty("mealplan.database-path", "");
        assertThatThrownBy(() ->
            new RuntimeProperties.Binding().runtimeProperties(env)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
