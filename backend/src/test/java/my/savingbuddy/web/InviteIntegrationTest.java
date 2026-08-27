package my.savingbuddy.web;

import com.jayway.jsonpath.JsonPath;
import my.savingbuddy.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invitations: admitting someone is a product action, not an env-var change.
 *
 * <p>Note what the setup demonstrates — in invite mode nothing can create the
 * FIRST user, since minting an invite requires an account. These tests seed the
 * owner directly, which is exactly why a real deployment must bootstrap in
 * {@code code} mode before switching to {@code invite}.
 */
@TestPropertySource(properties = "savingbuddy.registration.mode=invite")
class InviteIntegrationTest extends ApiTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PW = "long-enough-pw";

    private static String body(String email, String token) {
        return token == null
            ? "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PW)
            : "{\"email\":\"%s\",\"password\":\"%s\",\"inviteToken\":\"%s\"}".formatted(email, PW, token);
    }

    /**
     * Seeds the first account, which invite mode cannot create on its own.
     * Idempotent: the class shares one database across its tests.
     */
    private MockHttpSession seedOwner() throws Exception {
        Integer existing = jdbc.queryForObject(
            "select count(*) from users where email = 'owner@example.com'", Integer.class);
        if (existing == null || existing == 0) {
            jdbc.update("insert into users (email, password_hash, created_at) values (?, ?, current_timestamp)",
                "owner@example.com", passwordEncoder.encode(PW));
        }
        // Each test decides what invites should exist; the class shares one database.
        jdbc.update("delete from invites");
        return login("owner@example.com", PW);
    }

    private String mintToken(MockHttpSession as) throws Exception {
        String json = mvc.perform(post("/api/invites").session(as).with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(json).read("$.token", String.class);
    }

    @Test
    void anInviteAdmitsExactlyOneAccount() throws Exception {
        String token = mintToken(seedOwner());

        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("invited@example.com", token)))
            .andExpect(status().isCreated());

        // Single use: the same token must not admit a second account.
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("second@example.com", token)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", containsString("isn't valid")));
    }

    @Test
    void aMissingOrWrongTokenIsRefusedIdentically() throws Exception {
        seedOwner();
        String absent = mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("a@example.com", null)))
            .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        String wrong = mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("b@example.com", "not-a-real-token")))
            .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertThat(absent).isEqualTo(wrong);
    }

    @Test
    void aRejectedInviteLeavesNoAccountBehind() throws Exception {
        seedOwner();
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("ghost@example.com", "bogus")))
            .andExpect(status().isForbidden());
        // The user row is created before the claim, so a failed claim must roll it back.
        Integer rows = jdbc.queryForObject(
            "select count(*) from users where email = 'ghost@example.com'", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    void onlyAnAuthenticatedCallerCanMintOrListInvites() throws Exception {
        mvc.perform(post("/api/invites").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/invites")).andExpect(status().isUnauthorized());
    }

    @Test
    void theInviteListShowsWhoUsedItAndNeverTheTokenAgain() throws Exception {
        MockHttpSession o = seedOwner();
        String token = mintToken(o);
        mvc.perform(post("/api/auth/register").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("used@example.com", token)));

        doGet(o, "/api/invites")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("USED"))
            .andExpect(jsonPath("$[0].usedBy").value("used@example.com"))
            .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    void invitesAreScopedToTheirCreator() throws Exception {
        MockHttpSession o = seedOwner();
        String token = mintToken(o);
        mvc.perform(post("/api/auth/register").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("other@example.com", token)));
        MockHttpSession other = login("other@example.com", PW);

        // The invited user sees their own (empty) list, never the owner's.
        doGet(other, "/api/invites").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void theTokenIsNeverStoredInPlaintext() throws Exception {
        String token = mintToken(seedOwner());
        Integer plaintext = jdbc.queryForObject(
            "select count(*) from invites where token_hash = ?", Integer.class, token);
        assertThat(plaintext).isZero();
        Integer stored = jdbc.queryForObject("select count(*) from invites", Integer.class);
        assertThat(stored).isEqualTo(1);
    }

    @Test
    void anOwnerCannotHoardOpenInvites() throws Exception {
        MockHttpSession o = seedOwner();
        for (int i = 0; i < 5; i++) mintToken(o);
        mvc.perform(post("/api/invites").session(o).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("unused invites")));
    }
}
