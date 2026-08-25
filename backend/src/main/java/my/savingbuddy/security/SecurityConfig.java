package my.savingbuddy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Session-based auth for the SPA.
 *
 * <p>Register and login are the only open API endpoints; everything else under
 * {@code /api} requires an authenticated session and answers 401 (not a
 * redirect) when there is none. Non-API paths stay open — they serve the React
 * bundle, which handles its own routing.
 *
 * <p>CSRF uses the documented SPA recipe: the token is exposed in a cookie the
 * frontend can read ({@code XSRF-TOKEN}) and must be echoed back in the
 * {@code X-XSRF-TOKEN} header on every mutating request.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final boolean secureCookies;

    public SecurityConfig(@Value("${savingbuddy.secure-cookies:false}") boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // /registration is readable anonymously: a signed-out visitor is exactly
                // who needs it, to know whether to show a code field or hide sign-up.
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/registration").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .sessionManagement(session -> session
                // Registered so a password change can expire this user's OTHER
                // sessions. maximumSessions(-1) keeps concurrent logins allowed —
                // the registry is for eviction, not for limiting sign-ins.
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry())
                // The default strategy answers 200 with a prose sentence, which a
                // JSON client would parse as success. An expired session is
                // indistinguishable from no session, so say 401 like everything else.
                .expiredSessionStrategy(event -> {
                    var res = event.getResponse();
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write("{\"message\":\"Your session ended. Please sign in again.\",\"errors\":[]}");
                }))
            .logout(logout -> logout.disable());
        return http.build();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Required for the registry to learn about destroyed sessions; without it, entries leak. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * The CSRF cookie is written by this repository, not by the servlet container,
     * so {@code server.servlet.session.cookie.*} does not reach it. Both read the
     * same flag: a Secure session cookie beside a non-Secure CSRF cookie would
     * leak the token over plain HTTP while looking hardened.
     *
     * <p>HttpOnly stays false by design — the SPA has to read this one to echo it
     * back as a header.
     */
    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieCustomizer(cookie -> cookie
            .secure(secureCookies)
            .sameSite("Lax")
            .path("/"));
        return repo;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * From the Spring Security SPA documentation: XOR-encode the token when
     * rendering it (BREACH protection), but validate the raw value when it
     * arrives in a header, since the JavaScript client reads the raw cookie.
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            xor.handle(request, response, csrfToken);
            // Force the token to resolve so the repository writes the cookie.
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(headerValue) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /** Ensures the XSRF-TOKEN cookie is written on every response, so the SPA always has one before its first POST. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(request, response);
        }
    }
}
