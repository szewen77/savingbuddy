package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A personal install: one user, signed in automatically, no password prompt. */
@ActiveProfiles("local")
@TestPropertySource(properties = "server.address=127.0.0.1")
class LocalProfileIntegrationTest extends ApiTestBase {

    @Autowired JdbcTemplate jdbc;

    private void seedSingleUser() {
        jdbc.update("delete from users");
        jdbc.update("insert into users (email, password_hash, created_at) values (?, ?, current_timestamp)",
            "owner@localhost", "irrelevant-no-password-is-checked");
    }

    @Test
    void theSingleUserIsSignedInWithoutAnyCredential() throws Exception {
        seedSingleUser();
        // No session, no login call — the request arrives authenticated.
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("owner@localhost"));
    }

    @Test
    void withNoUsersItStaysOutOfTheWay() throws Exception {
        jdbc.update("delete from users");
        // A fresh install must still reach registration rather than being
        // silently signed in as nobody.
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void withSeveralUsersItRefusesToGuess() throws Exception {
        jdbc.update("delete from users");
        for (String email : new String[] {"a@example.com", "b@example.com"}) {
            jdbc.update("insert into users (email, password_hash, created_at) values (?, ?, current_timestamp)",
                email, "x");
        }
        // Two accounts means the instance is genuinely multi-user; picking one
        // would be a guess, so normal authentication applies.
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
