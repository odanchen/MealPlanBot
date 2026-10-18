package app.mealplan.config;
import org.springframework.boot.logging.*;
import org.springframework.context.annotation.Configuration;
/** Keep framework request/SQL/provider diagnostics from exposing private values. */
@Configuration(proxyBeanMethods=false)
public class LoggingConfiguration {
    public LoggingConfiguration() {
        var logging=LoggingSystem.get(getClass().getClassLoader());
        for(String name:new String[]{"org.springframework.security","org.springframework.web","org.springframework.jdbc","com.zaxxer.hikari","org.apache.coyote","org.apache.tomcat"})
            logging.setLogLevel(name,LogLevel.WARN);
    }
}
