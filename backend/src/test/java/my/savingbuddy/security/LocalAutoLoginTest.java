package my.savingbuddy.security;

import my.savingbuddy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guard that makes auto-login safe. This filter authenticates every request
 * with no credential, so the only thing standing between it and a wide-open
 * deployment is the bind-address check.
 */
class LocalAutoLoginTest {

    private final UserRepository users = Mockito.mock(UserRepository.class);

    @Test
    void refusesToConstructOnAnAddressOtherMachinesCanReach() {
        assertThatThrownBy(() -> new LocalAutoLogin(users, "0.0.0.0"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("loopback")
            .hasMessageContaining("SERVER_ADDRESS=127.0.0.1");
    }

    @Test
    void constructsOnLoopbackHoweverItIsWritten() {
        // Resolved, not string-matched, so both spellings are accepted.
        assertThatCode(() -> new LocalAutoLogin(users, "127.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> new LocalAutoLogin(users, "localhost")).doesNotThrowAnyException();
    }
}
