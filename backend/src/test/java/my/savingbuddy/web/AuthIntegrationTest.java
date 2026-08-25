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
    void theSpaItselfIsNotBehindTheAuthWall() throws Exception {
        // An anonymous visitor must be able to reach the login screen, so "/"
        // must never be gated (401). Whether a bundle is actually present is a
        // packaging concern — the backend test build runs with -DskipFrontend,
        // so asserting 200 here would test the build layout, not security.
        // CI's packaged-JAR smoke test covers the bundle being served.
        int status = mvc.perform(get("/")).andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
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
    void passwordCanBeChangedAndTheOldOneStopsWorking() throws Exception {
        MockHttpSession s = register("rotate@example.com", "the-old-password");
        doPost(s, "/api/auth/password",
                "{\"currentPassword\":\"the-old-password\",\"newPassword\":\"the-new-password\"}")
            .andExpect(status().isNoContent());

        // The caller keeps working — no sign-out mid-flow.
        doGet(s, "/api/auth/me").andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rotate@example.com\",\"password\":\"the-old-password\"}"))
            .andExpect(status().isUnauthorized());
        login("rotate@example.com", "the-new-password");
    }

    @Test
    void changingThePasswordRequiresTheCurrentOne() throws Exception {
        MockHttpSession s = register("guard@example.com", "the-real-password");
        doPost(s, "/api/auth/password",
                "{\"currentPassword\":\"not-the-password\",\"newPassword\":\"a-brand-new-one\"}")
            .andExpect(status().isBadRequest());
        // Unchanged: the original still works.
        login("guard@example.com", "the-real-password");
    }

    @Test
    void theNewPasswordMustDifferAndMeetTheLengthFloor() throws Exception {
        MockHttpSession s = register("rules@example.com", "the-old-password");
        doPost(s, "/api/auth/password",
                "{\"currentPassword\":\"the-old-password\",\"newPassword\":\"the-old-password\"}")
            .andExpect(status().isBadRequest());
        doPost(s, "/api/auth/password",
                "{\"currentPassword\":\"the-old-password\",\"newPassword\":\"short\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void changingThePasswordEvictsTheUsersOtherSessions() throws Exception {
        register("evict@example.com", "the-old-password");
        MockHttpSession attacker = login("evict@example.com", "the-old-password");
        MockHttpSession owner = login("evict@example.com", "the-old-password");
        doGet(attacker, "/api/auth/me").andExpect(status().isOk());

        doPost(owner, "/api/auth/password",
                "{\"currentPassword\":\"the-old-password\",\"newPassword\":\"the-new-password\"}")
            .andExpect(status().isNoContent());

        // The whole point: a stolen session must not survive the rotation.
        doGet(attacker, "/api/auth/me").andExpect(status().isUnauthorized());
        doGet(owner, "/api/auth/me").andExpect(status().isOk());
    }

    @Test
    void passwordChangeRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"a\",\"newPassword\":\"long-enough-pw\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void mutationsWithoutACsrfTokenAreRejected() throws Exception {
        MockHttpSession s = register("csrf@example.com", "long-enough-pw");
        mvc.perform(post("/api/transactions").session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10,\"category\":\"Groceries\"}"))
            .andExpect(status().isForbidden());
    }
}
