package app.mealplan.web;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
/** Only known page paths receive the shell; API/assets keep their real HTTP errors. */
@Configuration(proxyBeanMethods=false)
public class FrontendConfiguration implements WebMvcConfigurer {
 @Override public void addViewControllers(ViewControllerRegistry registry) {
  for(String path:new String[]{"/recipes/{slug}","/schedules","/schedules/{id}","/lists"}) registry.addViewController(path).setViewName("forward:/index.html");
 }
}
