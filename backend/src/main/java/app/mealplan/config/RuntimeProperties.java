package app.mealplan.config;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

public record RuntimeProperties(Path databasePath, String deploymentTarget, boolean fixtureAuth, URI publicBaseUrl) {
    public RuntimeProperties {
        databasePath = databasePath.toAbsolutePath().normalize();
        if (!Set.of("local", "test", "pi", "production").contains(deploymentTarget))
            throw new IllegalArgumentException("Invalid deployment target");
        if (fixtureAuth && !deploymentTarget.equals("local"))
            throw new IllegalArgumentException("Fixture authentication requires the local target");
        if (deploymentTarget.equals("local") && !databasePath.getParent().endsWith(Path.of(".local/mealplan")))
            throw new IllegalArgumentException("Local database must be isolated under .local/mealplan");
        if (!deploymentTarget.equals("local") && !deploymentTarget.equals("test")) {
            if (publicBaseUrl == null || !"https".equals(publicBaseUrl.getScheme()) || publicBaseUrl.getHost() == null
                    || publicBaseUrl.getUserInfo() != null || publicBaseUrl.getQuery() != null || publicBaseUrl.getFragment() != null
                    || !(publicBaseUrl.getPath().isEmpty() || publicBaseUrl.getPath().equals("/")))
                throw new IllegalArgumentException("A canonical production HTTPS origin is required");
        }
    }
    public boolean local() { return deploymentTarget.equals("local"); }

    @Configuration(proxyBeanMethods = false)
    public static class Binding {
        @Bean RuntimeProperties runtimeProperties(Environment env) {
            String target = env.getProperty("mealplan.deployment-target", "production");
            boolean fixture = env.getProperty("mealplan.fixture-auth", Boolean.class, false);
            boolean localProfile = Set.of(env.getActiveProfiles()).contains("local");
            if (fixture && !target.equals("local")) throw new IllegalArgumentException("Fixture authentication requires the local target");
            if (fixture && !localProfile) throw new IllegalArgumentException("Fixture authentication requires explicit local profile");
            String address = env.getProperty("server.address", "127.0.0.1");
            if (!Set.of("127.0.0.1", "::1").contains(address)) throw new IllegalArgumentException("Bind address must be loopback");
            if (!target.equals("test") && env.getProperty("server.port", Integer.class, 8000) != 8000)
                throw new IllegalArgumentException("Application port must be 8000");
            if (!target.equals("local") && !target.equals("test") &&
                    (!env.getProperty("server.servlet.session.cookie.secure", Boolean.class, true)
                    || !env.getProperty("server.servlet.session.cookie.http-only", Boolean.class, true)))
                throw new IllegalArgumentException("Production session cookies must be Secure and HttpOnly");
            if (!"lax".equalsIgnoreCase(env.getProperty("server.servlet.session.cookie.same-site", "lax"))
                    || env.getProperty("server.servlet.session.persistent", Boolean.class, false)
                    || !org.springframework.boot.convert.DurationStyle.detectAndParse(env.getProperty("server.servlet.session.timeout", "7d")).equals(java.time.Duration.ofDays(7)))
                throw new IllegalArgumentException("Sessions must be memory-only with SameSite=Lax and seven-day inactivity timeout");
            String origin = env.getProperty("mealplan.public-base-url");
            String databasePath = env.getRequiredProperty("mealplan.database-path");
            if (databasePath.isBlank()) throw new IllegalArgumentException("An explicit isolated database path is required");
            return new RuntimeProperties(Path.of(databasePath), target, fixture,
                    origin == null || origin.isBlank() ? null : URI.create(origin));
        }
    }
}
