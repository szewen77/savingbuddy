package my.savingbuddy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /**
     * Applies SameSite to every cookie at the container level.
     *
     * <p>Needed because CookieCsrfTokenRepository silently drops {@code sameSite}
     * set through its cookie customizer — {@code secure} and {@code path} apply,
     * SameSite does not. Doing it here also covers JSESSIONID, so both cookies
     * are guaranteed to agree rather than being configured in two places.
     */
    @Bean
    public CookieSameSiteSupplier sameSiteCookies() {
        return CookieSameSiteSupplier.ofLax();
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Kuala_Lumpur"));
    }

    @Bean
    public WebMvcConfigurer cors(@Value("${savingbuddy.cors.allowed-origins:http://localhost:5173}") String[] origins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}
