package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The throttle, end to end.
 *
 * <p>Limits are narrowed for THIS CLASS ONLY. Loosening them globally in the test
 * config would leave every other suite running with rate limiting effectively
 * off — including the auth tests, which is exactly where a regression would hide.
 */
@TestPropertySource(properties = {
    "savingbuddy.login-rate-limit.email-max-failures=3",
    "savingbuddy.login-rate-limit.ip-max-failures=50",
})
class LoginRateLimitIntegrationTest extends ApiTestBase {

    private org.springframework.test.web.servlet.ResultActions attempt(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    @Test
    void repeatedFailuresEventuallyGet429WithARetryHeader() throws Exception {
        register("throttled@example.com", "the-right-password");

        for (int i = 0; i < 3; i++) {
            attempt("throttled@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }

        attempt("throttled@example.com", "wrong-password")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.message", containsString("Too many sign-in attempts")));
    }

    @Test
    void theThrottleAppliesEvenWithTheCorrectPassword() throws Exception {
        register("locked-out@example.com", "the-right-password");
        for (int i = 0; i < 3; i++) attempt("locked-out@example.com", "nope").andExpect(status().isUnauthorized());

        // Otherwise an attacker learns the password is right from a 200 during a block.
        attempt("locked-out@example.com", "the-right-password").andExpect(status().isTooManyRequests());
    }

    @Test
    void aDifferentAccountIsUnaffected() throws Exception {
        register("noisy@example.com", "the-right-password");
        register("quiet@example.com", "the-right-password");
        for (int i = 0; i < 3; i++) attempt("noisy@example.com", "wrong").andExpect(status().isUnauthorized());

        // Per-address budgets: one account being attacked must not lock out another.
        attempt("quiet@example.com", "the-right-password").andExpect(status().isOk());
    }

    @Test
    void aSuccessfulSignInClearsTheCount() throws Exception {
        register("mistyper@example.com", "the-right-password");
        attempt("mistyper@example.com", "typo").andExpect(status().isUnauthorized());
        attempt("mistyper@example.com", "typo2").andExpect(status().isUnauthorized());
        attempt("mistyper@example.com", "the-right-password").andExpect(status().isOk());

        // Budget reset, so a later mistake does not immediately trip the limit.
        attempt("mistyper@example.com", "typo3").andExpect(status().isUnauthorized());
        attempt("mistyper@example.com", "typo4").andExpect(status().isUnauthorized());
        attempt("mistyper@example.com", "the-right-password").andExpect(status().isOk());
    }

    @Test
    void anOversizedEmailIsRejectedBeforeItBecomesACacheKey() throws Exception {
        attempt("x".repeat(5000) + "@example.com", "whatever")
            .andExpect(status().isBadRequest());
    }
}
