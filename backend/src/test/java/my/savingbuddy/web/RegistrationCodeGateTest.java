package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A deployment-shaped instance: a signup code is required. */
@TestPropertySource(properties = {
    "savingbuddy.registration.mode=code",
    "savingbuddy.registration.code=a-sufficiently-long-code",
})
class RegistrationCodeGateTest extends ApiTestBase {
    private static final String CODE = "a-sufficiently-long-code";

    private static String body(String email, String code) {
        return code == null
            ? "{\"email\":\"%s\",\"password\":\"long-enough-pw\"}".formatted(email)
            : "{\"email\":\"%s\",\"password\":\"long-enough-pw\",\"signupCode\":\"%s\"}".formatted(email, code);
    }

    @Test
    void theRightCodeCreatesAnAccount() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("invited@example.com", CODE)))
            .andExpect(status().isCreated());
    }

    @Test
    void aWrongOrMissingCodeIsRefusedIdentically() throws Exception {
        String wrong = mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("nope@example.com", "not-the-code-at-all")))
            .andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        String absent = mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("nope2@example.com", null)))
            .andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        // Identical: distinguishing them tells a prober what stands between them
        // and an account.
        assertThat(wrong).isEqualTo(absent).contains("signup code");
    }

    @Test
    void theModeIsReadableAnonymouslySoTheScreenCanAskForACode() throws Exception {
        mvc.perform(get("/api/auth/registration"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("code"));
    }

    @Test
    void guessingTheCodeIsThrottled() throws Exception {
        // A gate that can be hammered is not a gate. Registration shares the
        // login budget, so repeated wrong codes stop being answered.
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("guess@example.com", "wrong-guess-" + i)));
        }
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("guess@example.com", CODE)))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginIsUntouchedByTheGate() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("existing@example.com", CODE)));
        // An existing user must never be locked out by a registration setting.
        login("existing@example.com", "long-enough-pw");
    }
}
