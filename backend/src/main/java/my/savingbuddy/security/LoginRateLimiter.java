package my.savingbuddy.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Throttles failed sign-in attempts.
 *
 * <p>A throttle, never a lockout. Marking an account locked would hand anyone who
 * knows an email address a way to keep its owner out — turning a defence into a
 * denial-of-service tool. The window here heals by itself and needs no admin.
 *
 * <p>Keyed on IP <em>and</em> email, because either alone fails: IP-only punishes
 * everyone behind one NAT, and email-only lets an attacker spray many accounts
 * from a single host. Successful sign-ins clear both counters, so a legitimate
 * user who mistypes twice and then succeeds is never penalised.
 */
@Component
public class LoginRateLimiter {

    /** A fixed window: once spent, it does not extend — further failures cannot lengthen a block. */
    private record Window(Instant start, int failures) {}

    private final Cache<String, Window> byIp;
    private final Cache<String, Window> byEmail;
    private final Clock clock;
    private final Duration window;
    private final int ipMax;
    private final int emailMax;

    public LoginRateLimiter(Clock clock,
                            @Value("${savingbuddy.login-rate-limit.window:15m}") Duration window,
                            @Value("${savingbuddy.login-rate-limit.ip-max-failures:20}") int ipMax,
                            @Value("${savingbuddy.login-rate-limit.email-max-failures:5}") int emailMax) {
        this.clock = clock;
        this.window = window;
        this.ipMax = ipMax;
        this.emailMax = emailMax;
        // maximumSize bounds memory under attack; expireAfterWrite reclaims idle
        // entries. The window arithmetic below is what actually decides blocking.
        this.byIp = Caffeine.newBuilder().maximumSize(10_000)
            .expireAfterWrite(window.multipliedBy(2)).build();
        this.byEmail = Caffeine.newBuilder().maximumSize(10_000)
            .expireAfterWrite(window.multipliedBy(2)).build();
    }

    /** Throws if this caller has spent its budget. Checked before any password work. */
    public void checkAllowed(String ip, String email) {
        // IP first, and return without touching the email counter: otherwise an
        // attacker already blocked by IP could still burn a victim's email budget.
        long ipRetry = retryAfterSeconds(byIp.getIfPresent(key(ip)), ipMax);
        if (ipRetry > 0) throw new TooManyLoginAttemptsException(ipRetry);

        long emailRetry = retryAfterSeconds(byEmail.getIfPresent(key(email)), emailMax);
        if (emailRetry > 0) throw new TooManyLoginAttemptsException(emailRetry);
    }

    public void recordFailure(String ip, String email) {
        bump(byIp, key(ip));
        bump(byEmail, key(email));
    }

    /** A success clears both counters — a real user who mistyped is not left throttled. */
    public void recordSuccess(String ip, String email) {
        byIp.invalidate(key(ip));
        byEmail.invalidate(key(email));
    }

    private void bump(Cache<String, Window> cache, String key) {
        Instant now = Instant.now(clock);
        Window current = cache.getIfPresent(key);
        cache.put(key, current == null || expired(current, now)
            ? new Window(now, 1)
            : new Window(current.start(), current.failures() + 1));
    }

    private boolean expired(Window w, Instant now) {
        return !now.isBefore(w.start().plus(window));
    }

    /** Seconds left to wait, or 0 when the caller may proceed. */
    private long retryAfterSeconds(Window w, int max) {
        if (w == null) return 0;
        Instant now = Instant.now(clock);
        if (expired(w, now) || w.failures() < max) return 0;
        long remaining = Duration.between(now, w.start().plus(window)).getSeconds();
        return Math.max(1, remaining); // never advertise "retry in 0 seconds"
    }

    /**
     * Normalised, length-capped and hashed. Hashing keeps submitted addresses —
     * which may be other people's — out of a long-lived in-memory map, and the
     * cap stops an oversized value from being stored at all.
     */
    private static String key(String raw) {
        String normalised = raw == null ? "" : raw.trim().toLowerCase();
        if (normalised.length() > 255) normalised = normalised.substring(0, 255);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalised.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static class TooManyLoginAttemptsException extends RuntimeException {
        private final long retryAfterSeconds;

        public TooManyLoginAttemptsException(long retryAfterSeconds) {
            super("Too many sign-in attempts. Try again later.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}
