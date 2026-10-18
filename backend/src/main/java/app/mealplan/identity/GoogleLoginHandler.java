package app.mealplan.identity;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public final class GoogleLoginHandler implements AuthenticationSuccessHandler {

    public static final String RETURN_PATH = "mealplan.return-path";
    private final IdentityService identities;

    public GoogleLoginHandler(IdentityService identities) {
        this.identities = identities;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        var google = (OidcUser) authentication.getPrincipal();
        var account = identities.resolve(
            google.getIssuer().toString(),
            google.getSubject(),
            google.getFullName(),
            google.getEmail()
        );
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
        var session = request.getSession(false);
        String path =
            session == null ? "/" : safeReturnPath((String) session.getAttribute(RETURN_PATH));
        if (session != null) session.removeAttribute(RETURN_PATH);
        response.sendRedirect(path);
    }

    public static String safeReturnPath(String value) {
        if (
            value == null ||
            value.length() > 2048 ||
            !value.startsWith("/") ||
            value.startsWith("//") ||
            value.contains("\\")
        ) return "/";
        try {
            var uri = URI.create(value);
            String path = uri.getPath();
            if (
                uri.isAbsolute() ||
                uri.getRawAuthority() != null ||
                path == null ||
                !path.startsWith("/") ||
                path.contains("//") ||
                path.contains("\\") ||
                path.chars().anyMatch(c -> c < 32 || c == 127)
            ) return "/";
            if (
                path.startsWith("/api/") ||
                path.startsWith("/oauth2/") ||
                path.startsWith("/login/") ||
                path.startsWith("/auth/")
            ) return "/";
            return uri.normalize().toString();
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
