package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.AuthUser;
import my.savingbuddy.domain.User;
import my.savingbuddy.repository.UserRepository;
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

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public AuthUser register(String email, String rawPassword) {
        String normalised = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalised)) {
            throw new EmailTakenException("An account with that email already exists");
        }
        User user = users.save(new User(normalised, passwordEncoder.encode(rawPassword), Instant.now(clock)));
        return new AuthUser(user.getEmail());
    }

    public static class EmailTakenException extends RuntimeException {
        public EmailTakenException(String m) { super(m); }
    }
}
