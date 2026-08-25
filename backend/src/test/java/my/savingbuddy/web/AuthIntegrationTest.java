package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The front door: register, login, logout, and what an anonymous request gets. */
class AuthIntegrationTest extends ApiTestBase {

    @Test
    void anonymousRequestsGet401NotARedirect() throws Exception {
        mvc.perform(get("/api/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void theSpaItselfIsServedWithoutAuth() throws Exception {
        // The React bundle must load so an anonymous visitor can reach the login screen.
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void registerLogsTheUserInAndMeKnowsThem() throws Exception {
        MockHttpSession s = register("sara@example.com", "long-enough-pw");
        doGet(s, "/api/auth/me")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("sara@example.com"));
    }

    @Test
    void emailIsNormalisedAndDuplicatesAreRejected() throws Exception {
        register("Case@Example.com", "long-enough-pw");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"case@example.COM\",\"password\":\"whatever-else\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void weakOrMalformedRegistrationsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"long-enough-pw\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ok@example.com\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordAndUnknownEmailFailIdentically() throws Exception {
        register("known@example.com", "the-right-password");
        String wrongPw = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"known@example.com\",\"password\":\"the-wrong-password\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String noUser = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"the-wrong-password\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        // Same body for both, so responses cannot be used to enumerate accounts.
        org.assertj.core.api.Assertions.assertThat(wrongPw).isEqualTo(noUser);
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession s = register("leaver@example.com", "long-enough-pw");
        doGet(s, "/api/auth/me").andExpect(status().isOk());
        doPost(s, "/api/auth/logout", "");
        doGet(s, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void mutationsWithoutACsrfTokenAreRejected() throws Exception {
        MockHttpSession s = register("csrf@example.com", "long-enough-pw");
        mvc.perform(post("/api/transactions").session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10,\"category\":\"Groceries\"}"))
            .andExpect(status().isForbidden());
    }
}
