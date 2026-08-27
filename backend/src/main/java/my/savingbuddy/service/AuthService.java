package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.AuthUser;
import my.savingbuddy.domain.User;
import my.savingbuddy.repository.UserRepository;
import my.savingbuddy.security.RegistrationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Registration. Login itself is Spring Security's job; this only creates users. */
@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final RegistrationPolicy registrationPolicy;
    private final InviteService invites;
    private final RegistrationModeService modes;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock,
                       RegistrationPolicy registrationPolicy, InviteService invites,
                       RegistrationModeService modes) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.registrationPolicy = registrationPolicy;
        this.invites = invites;
        this.modes = modes;
    }

    @Transactional
    public AuthUser register(String email, String rawPassword, String signupCode, String inviteToken) {
        // The live mode may have been changed from the app, so it is read here
        // rather than taken from the environment-backed policy.
        RegistrationPolicy.Mode mode = modes.current();
        registrationPolicy.checkAs(mode, signupCode);

        String normalised = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalised)) {
            throw new EmailTakenException("An account with that email already exists");
        }
        User user = users.save(new User(normalised, passwordEncoder.encode(rawPassword), Instant.now(clock)));

        // Claimed after the user exists, because the invite records who used it —
        // and inside this transaction, so a rejected claim rolls the account back
        // rather than leaving one created by an invalid invite.
        if (mode == RegistrationPolicy.Mode.INVITE) {
            invites.claimFor(inviteToken, user.getId());
        }
        return new AuthUser(user.getEmail());
    }

    /**
     * Changes a password after re-verifying the current one.
     *
     * <p>Re-verification matters even though the caller is already authenticated:
     * a session left open on a shared machine would otherwise be enough to take
     * the account over permanently.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = users.findById(userId)
            .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IncorrectPasswordException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IncorrectPasswordException("The new password must be different from the current one");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    public static class IncorrectPasswordException extends RuntimeException {
        public IncorrectPasswordException(String m) { super(m); }
    }

    public static class EmailTakenException extends RuntimeException {
        public EmailTakenException(String m) { super(m); }
    }
}
