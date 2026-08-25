package my.savingbuddy.config;

import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {
    // No CORS bean here on purpose. One used to allow http://localhost:5173, but
    // SecurityConfig never calls .cors(), so it never took effect — and a
    // cookie-session app cannot work cross-origin without allowCredentials
    // anyway. Dev uses the Vite proxy, and production is single-origin: the API
    // and the SPA are served by the same JAR. Inert security config is worse
    // than none, because it reads as a control that exists.

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

}
