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

/** Password reset, mediated by whoever invited the account. */
@TestPropertySource(properties = "savingbuddy.registration.mode=invite")
class PasswordResetIntegrationTest extends ApiTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PW = "long-enough-pw";

    private MockHttpSession seedOwner() throws Exception {
        Integer existing = jdbc.queryForObject(
            "select count(*) from users where email = 'owner@example.com'", Integer.class);
        if (existing == null || existing == 0) {
            jdbc.update("insert into users (email, password_hash, created_at) values (?, ?, current_timestamp)",
                "owner@example.com", passwordEncoder.encode(PW));
        }
        return login("owner@example.com", PW);
    }

    /** Registers a member through an invite the owner minted, returning their user id. */
    private long inviteMember(MockHttpSession owner, String email) throws Exception {
        String invite = mvc.perform(post("/api/invites").session(owner).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.parse(invite).read("$.token", String.class);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"inviteToken\":\"%s\"}".formatted(email, PW, token)))
            .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private String mintReset(MockHttpSession owner, long targetId) throws Exception {
        String json = mvc.perform(post("/api/auth/reset/" + targetId).session(owner).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(json).read("$.token", String.class);
    }

    private static String redeem(String token, String newPassword) {
        return "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword);
    }

    @Test
    void theInviterCanResetAnAccountTheyInvited() throws Exception {
        MockHttpSession owner = seedOwner();
        long memberId = inviteMember(owner, "member@example.com");

        String token = mintReset(owner, memberId);
        mvc.perform(post("/api/auth/reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(redeem(token, "a-brand-new-password")))
            .andExpect(status().isNoContent());

        // The old password stops working; the new one signs in.
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"member@example.com\",\"password\":\"%s\"}".formatted(PW)))
            .andExpect(status().isUnauthorized());
        login("member@example.com", "a-brand-new-password");
    }

    @Test
    void aResetCodeWorksOnlyOnce() throws Exception {
        MockHttpSession owner = seedOwner();
        long id = inviteMember(owner, "once@example.com");
        String token = mintReset(owner, id);

        mvc.perform(post("/api/auth/reset").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(redeem(token, "first-new-password")))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(redeem(token, "second-new-password")))
            .andExpect(status().isForbidden());
        // The first reset stands.
        login("once@example.com", "first-new-password");
    }

    @Test
    void mintingAgainRetiresTheEarlierCode() throws Exception {
        MockHttpSession owner = seedOwner();
        long id = inviteMember(owner, "superseded@example.com");
        String first = mintReset(owner, id);
        String second = mintReset(owner, id);

        // A forwarded older message must stop working once a replacement exists.
        mvc.perform(post("/api/auth/reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(redeem(first, "via-old-code")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(redeem(second, "via-new-code")))
            .andExpect(status().isNoContent());
    }

    @Test
    void youCannotResetYourOwnAccount() throws Exception {
        MockHttpSession owner = seedOwner();
        long ownerId = jdbc.queryForObject("select id from users where email='owner@example.com'", Long.class);
        // Otherwise a stolen open session sets a new password without knowing the
        // old one — bypassing the current-password check on /api/auth/password.
        mvc.perform(post("/api/auth/reset/" + ownerId).session(owner).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", containsString("Change password")));
    }

    @Test
    void youCannotResetSomeoneYouDidNotInvite() throws Exception {
        MockHttpSession owner = seedOwner();
        long aId = inviteMember(owner, "peer-a@example.com");
        inviteMember(owner, "peer-b@example.com");
        MockHttpSession peerB = login("peer-b@example.com", PW);

        // Peers share an inviter but have no authority over each other.
        mvc.perform(post("/api/auth/reset/" + aId).session(peerB).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", containsString("only reset an account you invited")));
    }

    @Test
    void mintingRequiresAuthenticationButRedeemingDoesNot() throws Exception {
        mvc.perform(post("/api/auth/reset/1").with(csrf())).andExpect(status().isUnauthorized());
        // Redeeming must be public — the person using it cannot sign in. A bad
        // code is refused, not 401'd.
        mvc.perform(post("/api/auth/reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(redeem("not-a-real-code", "some-new-password")))
            .andExpect(status().isForbidden());
    }

    @Test
    void everyFailureReadsTheSame() throws Exception {
        MockHttpSession owner = seedOwner();
        long id = inviteMember(owner, "sameness@example.com");
        String token = mintReset(owner, id);
        mvc.perform(post("/api/auth/reset").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(redeem(token, "used-already-pw")));

        String used = mvc.perform(post("/api/auth/reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(redeem(token, "another-password")))
            .andReturn().getResponse().getContentAsString();
        String bogus = mvc.perform(post("/api/auth/reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(redeem("never-existed", "another-password")))
            .andReturn().getResponse().getContentAsString();
        assertThat(used).isEqualTo(bogus);
    }

    @Test
    void theCodeIsNeverStoredInPlaintext() throws Exception {
        MockHttpSession owner = seedOwner();
        long id = inviteMember(owner, "hashed@example.com");
        String token = mintReset(owner, id);
        Integer plaintext = jdbc.queryForObject(
            "select count(*) from password_resets where token_hash = ?", Integer.class, token);
        assertThat(plaintext).isZero();
    }

    @Test
    void resettingEndsEverySessionTheAccountHad() throws Exception {
        MockHttpSession owner = seedOwner();
        long id = inviteMember(owner, "evicted@example.com");
        MockHttpSession theirSession = login("evicted@example.com", PW);
        doGet(theirSession, "/api/auth/me").andExpect(status().isOk());

        String token = mintReset(owner, id);
        mvc.perform(post("/api/auth/reset").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(redeem(token, "recovered-password")))
            .andExpect(status().isNoContent());

        // Someone recovering may be recovering from a session they do not control.
        doGet(theirSession, "/api/auth/me").andExpect(status().isUnauthorized());
    }
}
