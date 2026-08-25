package my.savingbuddy;

import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for full-stack API tests.
 *
 * <p>Each test class gets its own database (unique in-memory name per context,
 * plus {@code @DirtiesContext} so Spring's context cache cannot hand two classes
 * the same one), and authenticates through the real endpoints — register or
 * login, session cookie, CSRF header — so the tests cover what a browser does,
 * not a shortcut around it.
 *
 * <p>{@code PER_CLASS} lifecycle lets ordered tests share the {@code session}
 * field established once in a non-static {@code @BeforeAll}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ApiTestBase {
    protected static final String DEMO_EMAIL = "demo@savingbuddy.local";
    protected static final String DEMO_PASSWORD = "demo12345";

    @Autowired protected MockMvc mvc;

    /** The authenticated session most tests act in. Subclasses set it in @BeforeAll. */
    protected MockHttpSession session;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String name = "sb-" + UUID.randomUUID();
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    protected MockHttpSession register(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
            .andExpect(status().isCreated())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected MockHttpSession login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    // ---- Requests in the default session ----

    protected ResultActions doGet(String url) throws Exception {
        return mvc.perform(get(url).session(session));
    }

    protected ResultActions doGet(String url, String param, String value) throws Exception {
        return mvc.perform(get(url).param(param, value).session(session));
    }

    protected ResultActions doPost(String url, String json) throws Exception {
        return mvc.perform(post(url).session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions doPut(String url, String json) throws Exception {
        return mvc.perform(put(url).session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions doDelete(String url) throws Exception {
        return mvc.perform(delete(url).session(session).with(csrf()));
    }

    protected ResultActions doDelete(MockHttpSession as, String url) throws Exception {
        return mvc.perform(delete(url).session(as).with(csrf()));
    }

    protected ResultActions doPut(MockHttpSession as, String url, String json) throws Exception {
        return mvc.perform(put(url).session(as).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---- The same, as a specific user (for isolation tests) ----

    protected ResultActions doGet(MockHttpSession as, String url) throws Exception {
        return mvc.perform(get(url).session(as));
    }

    protected ResultActions doGet(MockHttpSession as, String url, String param, String value) throws Exception {
        return mvc.perform(get(url).param(param, value).session(as));
    }

    protected ResultActions doPost(MockHttpSession as, String url, String json) throws Exception {
        return mvc.perform(post(url).session(as).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
