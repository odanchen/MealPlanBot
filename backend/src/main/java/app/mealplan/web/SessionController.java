package app.mealplan.web;

import app.mealplan.config.RuntimeProperties;
import app.mealplan.identity.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class SessionController {

    private final IdentityService identities;
    private final RuntimeProperties runtime;

    public SessionController(IdentityService identities, RuntimeProperties runtime) {
        this.identities = identities;
        this.runtime = runtime;
    }

    @GetMapping("/api/session")
    public Map<String, Object> session(Authentication authentication) {
        var result = new LinkedHashMap<String, Object>();
        boolean signedIn =
            authentication != null &&
            authentication.isAuthenticated() &&
            !(authentication instanceof AnonymousAuthenticationToken);
        result.put("authenticated", signedIn);
        result.put("authMode", runtime.local() ? "local" : "google");
        if (signedIn) {
            var user = identities.find(authentication.getName()).orElseThrow(ApiErrors::notFound);
            result.put("userId", user.id());
            result.put("displayName", user.displayName());
            result.put("email", user.email());
        }
        return result;
    }

    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/auth/login")
    public void login(
        @RequestParam(required = false) String returnTo,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws java.io.IOException {
        if (runtime.local()) {
            response.sendRedirect(GoogleLoginHandler.safeReturnPath(returnTo));
            return;
        }
        request
            .getSession()
            .setAttribute(
                GoogleLoginHandler.RETURN_PATH,
                GoogleLoginHandler.safeReturnPath(returnTo)
            );
        response.sendRedirect("/oauth2/authorization/google");
    }

    @GetMapping(value = "/signin-error", produces = MediaType.TEXT_HTML_VALUE)
    public String signInError() {
        return "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Sign-in unsuccessful</title><main><h1>Sign-in was not completed</h1><p>You can try Google sign-in again.</p><a href=\"/auth/login\">Try again</a> <a href=\"/\">Return to recipes</a></main></html>";
    }
}
