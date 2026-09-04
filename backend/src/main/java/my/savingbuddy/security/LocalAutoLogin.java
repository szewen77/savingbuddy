package my.savingbuddy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import my.savingbuddy.domain.User;
import my.savingbuddy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

/**
 * Signs the single local user in automatically, so a personal install on your own
 * machine does not ask for a password every time.
 *
 * <p>Active only under the {@code local} profile, and only when the server is
 * bound to a loopback address — the constructor refuses to start otherwise. Both
 * guards are deliberate: this filter hands every request a fully authenticated
 * session with no credential, so anything that could reach the port would own the
 * account. It must be impossible to switch on by accident in a deployment.
 *
 * <p>It also does nothing unless there is exactly one user. Zero means the app has
 * not been set up yet and registration should run normally; more than one means
 * the instance is genuinely multi-user and picking a user would be a guess.
 */
@Component
@Profile("local")
public class LocalAutoLogin extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(LocalAutoLogin.class);

    private final UserRepository users;
    private final String bindAddress;

    public LocalAutoLogin(UserRepository users,
                          @Value("${server.address:0.0.0.0}") String bindAddress) {
        this.users = users;
        this.bindAddress = bindAddress;
        refuseIfReachable();
    }

    /**
     * Refuses to construct if the app is reachable from anywhere but this machine.
     * Resolved rather than string-matched, so "localhost" and "127.0.0.1" both
     * pass and "0.0.0.0" does not.
     *
     * <p>In the constructor rather than {@code @PostConstruct}: the container
     * rejects a lifecycle callback that declares a checked exception, and a guard
     * this important should run before the object exists at all.
     */
    final void refuseIfReachable() {
        InetAddress address;
        try {
            address = InetAddress.getByName(bindAddress);
        } catch (IOException e) {
            throw new IllegalStateException("Could not resolve server.address '" + bindAddress + "'", e);
        }
        if (!address.isLoopbackAddress()) {
            throw new IllegalStateException(
                "Profile 'local' signs every request in with no password, so it only makes sense on a "
                    + "loopback bind — but server.address is " + bindAddress + ". Remove the 'local' profile, "
                    + "or set SERVER_ADDRESS=127.0.0.1.");
        }
        log.warn("Profile 'local' is active: requests are signed in automatically, with no password. "
            + "This is for a personal install on {} only.", bindAddress);
    }

    /**
     * Keeps Spring Boot from ALSO registering this as a plain servlet filter.
     * It belongs in the security chain only; auto-registration would run it twice
     * and outside the chain's ordering.
     */
    @org.springframework.context.annotation.Bean
    static org.springframework.boot.web.servlet.FilterRegistrationBean<LocalAutoLogin>
            disableServletAutoRegistration(LocalAutoLogin filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            List<User> all = users.findAll();
            if (all.size() == 1) {
                AppUserDetails principal = new AppUserDetails(all.get(0));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }
}
