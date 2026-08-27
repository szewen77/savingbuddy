package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registration mode is owned by the app, not by an environment variable. */
@TestPropertySource(properties = "savingbuddy.registration.mode=closed")
class RegistrationModeTest extends ApiTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    private MockHttpSession owner() throws Exception {
        Integer existing = jdbc.queryForObject(
            "select count(*) from users where email = 'owner@example.com'", Integer.class);
        if (existing == null || existing == 0) {
            jdbc.update("insert into users (email, password_hash, created_at) values (?, ?, current_timestamp)",
                "owner@example.com", passwordEncoder.encode("long-enough-pw"));
        }
        return login("owner@example.com", "long-enough-pw");
    }

    @Test
    void theEnvironmentSuppliesTheModeUntilOneIsChosen() throws Exception {
        jdbc.update("delete from app_settings");
        mvc.perform(get("/api/auth/registration")).andExpect(jsonPath("$.mode").value("closed"));
    }

    @Test
    void aSignedInUserCanOpenAndCloseRegistrationWithoutARedeploy() throws Exception {
        MockHttpSession o = owner();

        doPut(o, "/api/auth/registration", "{\"mode\":\"invite\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("invite"));
        // Publicly visible immediately, so the sign-up screen reflects it.
        mvc.perform(get("/api/auth/registration")).andExpect(jsonPath("$.mode").value("invite"));

        doPut(o, "/api/auth/registration", "{\"mode\":\"closed\"}")
            .andExpect(jsonPath("$.mode").value("closed"));
        mvc.perform(get("/api/auth/registration")).andExpect(jsonPath("$.mode").value("closed"));
    }

    @Test
    void anAnonymousCallerCannotOpenRegistration() throws Exception {
        // The GET is public so the sign-in screen can render; the PUT must not be.
        mvc.perform(put("/api/auth/registration").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"invite\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void modesThatDependOnAHostSecretCannotBeSelectedFromTheApp() throws Exception {
        MockHttpSession o = owner();
        // `open` would admit the whole internet; `code` needs a secret the app cannot supply.
        doPut(o, "/api/auth/registration", "{\"mode\":\"open\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("closed or invite")));
        doPut(o, "/api/auth/registration", "{\"mode\":\"code\"}")
            .andExpect(status().isBadRequest());
        doPut(o, "/api/auth/registration", "{\"mode\":\"nonsense\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void theChosenModeActuallyGovernsRegistration() throws Exception {
        MockHttpSession o = owner();
        doPut(o, "/api/auth/registration", "{\"mode\":\"closed\"}").andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nope@example.com\",\"password\":\"long-enough-pw\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", containsString("closed")));
    }
}
