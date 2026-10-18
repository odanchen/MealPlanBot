package app.mealplan.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import app.mealplan.MealPlanApplication;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = MealPlanApplication.class,
    properties = { "mealplan.deployment-target=test", "mealplan.fixture-auth=false" }
)
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class IdentitySecurityTest {

    static final Path DIRECTORY;

    static {
        try {
            DIRECTORY = Files.createTempDirectory("mealplan-security-");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("mealplan.database-path", () -> DIRECTORY.resolve("app.sqlite").toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityService identities;

    @Test
    void browserFixtureHeadersAndEndpointsAreUnavailableInTheApplication() throws Exception {
        mvc.perform(get("/api/lists").header("X-Test-Principal", "pretend-owner")).andExpect(
            status().isUnauthorized()
        );
        mvc.perform(
            post("/__test/advance")
                .param("hours", "72")
                .with(
                    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(
                        app.mealplan.support.SqliteTestSupport.principal(
                            "00000000-0000-4000-8000-000000000001"
                        )
                    )
                )
                .with(
                    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
                )
        ).andExpect(status().isNotFound());
    }

    @Test
    void emailChangesPreserveUuidAndMatchingEmailsDoNotMerge() {
        var first = identities.resolve(
            "https://accounts.google.com",
            "subject-a",
            "First",
            "old@example.com"
        );
        var changed = identities.resolve(
            "accounts.google.com",
            "subject-a",
            "Renamed",
            "shared@example.com"
        );
        var other = identities.resolve(
            "https://accounts.google.com",
            "subject-b",
            "Other",
            "shared@example.com"
        );
        org.assertj.core.api.Assertions.assertThat(changed.id()).isEqualTo(first.id());
        org.assertj.core.api.Assertions.assertThat(changed.email()).isEqualTo("shared@example.com");
        org.assertj.core.api.Assertions.assertThat(other.id()).isNotEqualTo(first.id());
    }

    @Test
    void logoutEndsSession() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        var context =
            org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            app.mealplan.support.SqliteTestSupport.principal("00000000-0000-4000-8000-000000000001")
        );
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        mvc.perform(get("/api/lists").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lists").isEmpty());
        mvc.perform(
            post("/api/logout")
                .session(session)
                .with(
                    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
                )
        ).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void restartDoesNotRestoreAuthentication() throws Exception {
        // A stale browser cookie without this process's server-side session grants no access.
        mvc.perform(
            get("/api/lists").cookie(
                new jakarta.servlet.http.Cookie("JSESSIONID", "previous-process-session")
            )
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void localStubCannotActivateOnPi() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new app.mealplan.config.RuntimeProperties(
                Path.of("/private/tmp/app.sqlite"),
                "pi",
                true,
                java.net.URI.create("https://example.com")
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionIsPublicAndPrivateRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(header().string("Cache-Control", "no-store"));
        for (String route : new String[] {
            "/api/user-schedules",
            "/api/schedule-templates",
            "/api/lists",
        })
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
    }

    @Test
    void oauthUsesCanonicalCallbackAndOnlyIdentityScopes() throws Exception {
        var result = mvc
            .perform(
                get("/oauth2/authorization/google")
                    .header("Host", "evil.test")
                    .header("X-Forwarded-Host", "evil.test")
            )
            .andExpect(status().is3xxRedirection())
            .andReturn();
        String location = java.net.URLDecoder.decode(
            result.getResponse().getHeader("Location"),
            java.nio.charset.StandardCharsets.UTF_8
        );
        org.assertj.core.api.Assertions.assertThat(location)
            .startsWith("https://accounts.google.com/")
            .contains("redirect_uri=https://example.test/login/oauth2/code/google")
            .contains("scope=openid email profile")
            .doesNotContain("evil.test");
    }

    @Test
    void csrfIsRequiredForLogoutAndSessionInvalidation() throws Exception {
        mvc.perform(post("/api/logout")).andExpect(status().isForbidden());
        mvc.perform(get("/api/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString());
    }

    @Autowired
    GoogleLoginHandler loginHandler;

    @Test
    void trustedOidcPrincipalBecomesApplicationUuidAndReturnsToIntendedPath() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        request
            .getSession()
            .setAttribute(GoogleLoginHandler.RETURN_PATH, "/schedules/own-schedule");
        var now = java.time.Instant.now();
        var token = new org.springframework.security.oauth2.core.oidc.OidcIdToken(
            "test-only-provider-token",
            now,
            now.plusSeconds(300),
            java.util.Map.of(
                "iss",
                "https://accounts.google.com",
                "sub",
                "handler-subject",
                "aud",
                java.util.List.of("test-client"),
                "email",
                "cook@example.com",
                "name",
                "Cook"
            )
        );
        var user = new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(
            java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")
            ),
            token
        );
        var auth =
            new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                user,
                user.getAuthorities(),
                "google"
            );
        try {
            loginHandler.onAuthenticationSuccess(request, response, auth);
            var context = (org.springframework.security.core.context.SecurityContext) request
                .getSession()
                .getAttribute("SPRING_SECURITY_CONTEXT");
            org.assertj.core.api.Assertions.assertThat(
                java.util.UUID.fromString(context.getAuthentication().getName())
            ).isNotNull();
            org.assertj.core.api.Assertions.assertThat(response.getRedirectedUrl()).isEqualTo(
                "/schedules/own-schedule"
            );
            org.assertj.core.api.Assertions.assertThat(
                response.getContentAsString()
            ).doesNotContain("test-only-provider-token");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void cancelledLoginShowsSafePublicErrorWithoutAuthentication() throws Exception {
        mvc.perform(get("/login/oauth2/code/google").param("error", "access_denied"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signin-error"));
        mvc.perform(get("/signin-error"))
            .andExpect(status().isOk())
            .andExpect(
                content().string(org.hamcrest.Matchers.containsString("Sign-in was not completed"))
            );
    }
}
