package app.mealplan.identity;
import app.mealplan.config.RuntimeProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods=false)
public class SecurityConfiguration {
 @Bean SecurityFilterChain security(HttpSecurity http,RuntimeProperties runtime,Environment env,GoogleLoginHandler success) throws Exception {
  http.authorizeHttpRequests(auth -> auth
   .requestMatchers(HttpMethod.GET,"/","/index.html","/assets/**","/recipes/**","/schedules","/schedules/**","/lists","/api/recipes","/api/recipes/**","/api/ingredients","/api/glossary","/api/session","/api/csrf","/signin-error","/auth/login","/oauth2/authorization/google","/login/oauth2/code/google").permitAll()
   .requestMatchers(HttpMethod.POST,"/api/local-session").permitAll()
   .anyRequest().authenticated());
  http.csrf(Customizer.withDefaults());
  http.requestCache(cache -> cache.requestCache(new NullRequestCache()));
  http.exceptionHandling(errors -> errors
   .authenticationEntryPoint((req,res,e) -> json(res,401,"unauthenticated","Sign in to continue"))
   .accessDeniedHandler((req,res,e) -> json(res,403,"forbidden","Action is not permitted")));
  http.logout(logout -> logout.logoutUrl("/api/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
   .logoutSuccessHandler((req,res,auth) -> { res.setHeader("Cache-Control","no-store");res.setStatus(204); }));
  http.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
  http.addFilterBefore(new OncePerRequestFilter() {
   @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws IOException,ServletException {
    if(req.getRequestURI().startsWith("/api/") || req.getRequestURI().startsWith("/auth/") || req.getRequestURI().startsWith("/login/") || req.getRequestURI().startsWith("/oauth2/")) res.setHeader("Cache-Control","no-store");
    chain.doFilter(req,res);
   }
  }, SecurityContextHolderFilter.class);
  if(!runtime.local()) {
   boolean test=runtime.deploymentTarget().equals("test");
   String id=test ? "test-client" : env.getRequiredProperty("GOOGLE_CLIENT_ID");
   String secret=test ? "test-only" : env.getRequiredProperty("GOOGLE_CLIENT_SECRET");
   String origin=test ? "https://example.test" : runtime.publicBaseUrl().toString().replaceAll("/$","");
   var registration=CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(id).clientSecret(secret)
    .scope("openid","email","profile").redirectUri(origin+"/login/oauth2/code/google").build();
   http.oauth2Login(oauth -> oauth.clientRegistrationRepository(new InMemoryClientRegistrationRepository(registration))
    .successHandler(success).failureHandler((req,res,e) -> {
      var session=req.getSession(false); if(session!=null) session.removeAttribute(GoogleLoginHandler.RETURN_PATH);
      res.sendRedirect("/signin-error");
    }));
  }
  return http.build();
 }
 private static void json(HttpServletResponse response,int status,String code,String message) throws IOException {
  response.setStatus(status);response.setContentType("application/json");response.setHeader("Cache-Control","no-store");
  response.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\"}");
 }
}
