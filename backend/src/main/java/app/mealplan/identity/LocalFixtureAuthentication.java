package app.mealplan.identity;

import app.mealplan.config.RuntimeProperties;
import app.mealplan.web.ApiErrors;
import jakarta.servlet.http.*;
import java.net.InetAddress;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
public class LocalFixtureAuthentication {

    public static final String USER_ID = "c05853cb-3ee9-4d8b-b381-6734d26e0d03";
    private final RuntimeProperties runtime;
    private final IdentityService identities;

    public LocalFixtureAuthentication(RuntimeProperties runtime, IdentityService identities) {
        if (!runtime.local() || !runtime.fixtureAuth()) throw new IllegalArgumentException(
            "Local fixture authentication is unavailable"
        );
        this.runtime = runtime;
        this.identities = identities;
        identities.seedFixture(USER_ID);
    }

    @PostMapping("/api/local-session")
    public void signIn(HttpServletRequest request, HttpServletResponse response)
        throws java.io.IOException {
        if (
            !runtime.local() || !InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()
        ) throw ApiErrors.forbidden();
        var account = identities.find(USER_ID).orElseThrow();
        var old = request.getSession(false);
        if (old != null) old.invalidate();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            new UsernamePasswordAuthenticationToken(
                account.id(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            )
        );
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(204);
    }
}
