package my.savingbuddy.web;

import my.savingbuddy.FixedClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cookie hardening, asserted against a real servlet container.
 *
 * <p>Deliberately not a MockMvc test. SameSite is applied by Tomcat's cookie
 * processor, and MockMvc has no cookie processor — a MockMvc assertion here
 * passes or fails for reasons unrelated to what a browser would receive. It also
 * never emits JSESSIONID at all.
 *
 * <p>Runs with secure cookies on, which is the deployment posture; the local
 * default is off because loopback is plain HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FixedClockConfig.class)
class CookieAttributesTest {

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:mem:ck-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("savingbuddy.secure-cookies", () -> true);
        registry.add("server.servlet.session.cookie.secure", () -> true);
        registry.add("savingbuddy.backup.mode", () -> "none");
    }

    @LocalServerPort int port;

    private List<String> cookiesFrom(String path) {
        return RestClient.create().get()
            .uri("http://localhost:" + port + path)
            .exchange((req, res) -> res.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
    }

    private static String named(List<String> cookies, String name) {
        return cookies.stream().filter(c -> c.startsWith(name + "=")).findFirst()
            .orElseThrow(() -> new AssertionError("no " + name + " in " + cookies));
    }

    @Test
    void bothCookiesAreSecureAndSameSite() {
        // A 401 still sets both cookies, which is what the SPA needs before login.
        List<String> cookies = cookiesFrom("/api/auth/me");

        String xsrf = named(cookies, "XSRF-TOKEN");
        assertThat(xsrf).contains("Secure").contains("SameSite=Lax").contains("Path=/");
        // Must stay readable — the SPA echoes it back as a header.
        assertThat(xsrf).doesNotContain("HttpOnly");

        String session = named(cookies, "JSESSIONID");
        assertThat(session).contains("Secure").contains("SameSite=Lax").contains("HttpOnly");
    }
}
