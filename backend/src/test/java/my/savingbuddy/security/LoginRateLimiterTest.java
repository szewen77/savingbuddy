package my.savingbuddy.security;

import my.savingbuddy.security.LoginRateLimiter.TooManyLoginAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Window arithmetic, driven by a clock the test moves by hand rather than by sleeping. */
class LoginRateLimiterTest {
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-08-25T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private MovableClock clock;

    private LoginRateLimiter limiter(int ipMax, int emailMax) {
        clock = new MovableClock();
        return new LoginRateLimiter(clock, WINDOW, ipMax, emailMax);
    }

    @Test
    void failuresBelowTheBudgetAreAllowed() {
        LoginRateLimiter limiter = limiter(20, 3);
        for (int i = 0; i < 2; i++) limiter.recordFailure("1.2.3.4", "a@example.com");
        assertThatCode(() -> limiter.checkAllowed("1.2.3.4", "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void spendingTheEmailBudgetBlocksWithARetryHint() {
        LoginRateLimiter limiter = limiter(20, 3);
        for (int i = 0; i < 3; i++) limiter.recordFailure("1.2.3.4", "a@example.com");

        assertThatThrownBy(() -> limiter.checkAllowed("1.2.3.4", "a@example.com"))
            .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, WINDOW.getSeconds()));
    }

    @Test
    void theWindowHealsByItself() {
        LoginRateLimiter limiter = limiter(20, 3);
        for (int i = 0; i < 3; i++) limiter.recordFailure("1.2.3.4", "a@example.com");
        assertThatThrownBy(() -> limiter.checkAllowed("1.2.3.4", "a@example.com"))
            .isInstanceOf(TooManyLoginAttemptsException.class);

        clock.advance(WINDOW.plusSeconds(1));

        // No admin action, no unlock endpoint — the block simply ends.
        assertThatCode(() -> limiter.checkAllowed("1.2.3.4", "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void furtherFailuresDoNotExtendAnActiveBlock() {
        LoginRateLimiter limiter = limiter(20, 3);
        for (int i = 0; i < 3; i++) limiter.recordFailure("1.2.3.4", "a@example.com");

        clock.advance(Duration.ofMinutes(14));
        limiter.recordFailure("1.2.3.4", "a@example.com"); // still inside the window
        clock.advance(Duration.ofMinutes(2));              // past the ORIGINAL window

        // Fixed window: an attacker must not be able to hold a victim out forever
        // by continuing to fail against their address.
        assertThatCode(() -> limiter.checkAllowed("1.2.3.4", "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessClearsTheCountersSoAMistypeIsNotPunished() {
        LoginRateLimiter limiter = limiter(20, 3);
        limiter.recordFailure("1.2.3.4", "a@example.com");
        limiter.recordFailure("1.2.3.4", "a@example.com");
        limiter.recordSuccess("1.2.3.4", "a@example.com");

        limiter.recordFailure("1.2.3.4", "a@example.com");
        assertThatCode(() -> limiter.checkAllowed("1.2.3.4", "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void oneAddressesFailuresDoNotBlockAnother() {
        LoginRateLimiter limiter = limiter(20, 3);
        for (int i = 0; i < 3; i++) limiter.recordFailure("1.2.3.4", "victim@example.com");
        assertThatCode(() -> limiter.checkAllowed("1.2.3.4", "someone-else@example.com"))
            .doesNotThrowAnyException();
    }

    @Test
    void aBlockedIpDoesNotBurnTheEmailBudgetItIsGuessing() {
        LoginRateLimiter limiter = limiter(3, 5);
        for (int i = 0; i < 3; i++) limiter.recordFailure("9.9.9.9", "spray-" + i + "@example.com");

        assertThatThrownBy(() -> limiter.checkAllowed("9.9.9.9", "victim@example.com"))
            .isInstanceOf(TooManyLoginAttemptsException.class);

        // The victim's own budget must be untouched, or an already-blocked IP
        // could still be used to lock a known address out.
        assertThatCode(() -> limiter.checkAllowed("5.5.5.5", "victim@example.com")).doesNotThrowAnyException();
    }

    @Test
    void keysAreNormalisedAndOversizedInputIsBounded() {
        LoginRateLimiter limiter = limiter(20, 3);
        for (int i = 0; i < 3; i++) limiter.recordFailure("1.2.3.4", "  Case@Example.COM ");

        // Different casing must hit the same bucket, or the limit is trivially bypassed.
        assertThatThrownBy(() -> limiter.checkAllowed("1.2.3.4", "case@example.com"))
            .isInstanceOf(TooManyLoginAttemptsException.class);

        assertThatCode(() -> limiter.recordFailure("1.2.3.4", "x".repeat(100_000)))
            .doesNotThrowAnyException();
    }
}
