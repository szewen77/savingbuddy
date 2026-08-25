package my.savingbuddy.web;

import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An instance that has finished provisioning its people. */
@TestPropertySource(properties = "savingbuddy.registration.mode=closed")
class RegistrationClosedTest extends ApiTestBase {

    @Test
    void nobodyCanRegisterEvenWithACode() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"late@example.com\",\"password\":\"long-enough-pw\","
                    + "\"signupCode\":\"a-sufficiently-long-code\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", containsString("closed")));
    }

    @Test
    void theModeIsAdvertisedSoTheSignUpLinkCanBeHidden() throws Exception {
        mvc.perform(get("/api/auth/registration")).andExpect(jsonPath("$.mode").value("closed"));
    }
}
