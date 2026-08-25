package my.savingbuddy.security;

import my.savingbuddy.security.RegistrationPolicy.Mode;
import my.savingbuddy.security.RegistrationPolicy.RegistrationNotAllowedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The config-level guards, which are the ones that decide whether a deployment is accidentally open. */
class RegistrationPolicyTest {

    @Test
    void closedRefusesEveryone() {
        assertThatThrownBy(() -> new RegistrationPolicy(Mode.CLOSED, "").check("anything"))
            .isInstanceOf(RegistrationNotAllowedException.class)
            .hasMessageContaining("closed");
    }

    @Test
    void openAcceptsWithoutACode() {
        assertThatCode(() -> new RegistrationPolicy(Mode.OPEN, "").check(null)).doesNotThrowAnyException();
    }

    @Test
    void codeModeAcceptsOnlyTheExactCode() {
        RegistrationPolicy policy = new RegistrationPolicy(Mode.CODE, "a-sufficiently-long-code");
        assertThatCode(() -> policy.check("a-sufficiently-long-code")).doesNotThrowAnyException();
        // Surrounding whitespace is forgiven; a wrong value is not.
        assertThatCode(() -> policy.check("  a-sufficiently-long-code  ")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.check("a-sufficiently-long-cod")).isInstanceOf(RegistrationNotAllowedException.class);
        assertThatThrownBy(() -> policy.check("")).isInstanceOf(RegistrationNotAllowedException.class);
        assertThatThrownBy(() -> policy.check(null)).isInstanceOf(RegistrationNotAllowedException.class);
    }

    @Test
    void codeModeWithoutAUsableCodeRefusesToStart() {
        // Believing registration is gated when it is not is the failure this prevents.
        assertThatThrownBy(() -> new RegistrationPolicy(Mode.CODE, "").verifyConfigured())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("REGISTRATION_CODE");
        assertThatThrownBy(() -> new RegistrationPolicy(Mode.CODE, "too-short").verifyConfigured())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theBootFailureIsNarrow() {
        // It must never strand an instance whose existing users only want to sign in.
        assertThatCode(() -> new RegistrationPolicy(Mode.CLOSED, "").verifyConfigured()).doesNotThrowAnyException();
        assertThatCode(() -> new RegistrationPolicy(Mode.OPEN, "").verifyConfigured()).doesNotThrowAnyException();
    }

    @Test
    void theRequestDtoNeverPrintsItsSecrets() {
        String printed = new my.savingbuddy.api.Dtos.RegisterRequest(
            "a@example.com", "hunter2-the-password", "the-signup-code").toString();
        assertThat(printed).doesNotContain("hunter2-the-password").doesNotContain("the-signup-code");
    }
}
