package my.savingbuddy.security;

import my.savingbuddy.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * The authenticated principal. Carries the user's id so request handling can
 * scope queries without another lookup — the id comes from the session, never
 * from anything the client sends.
 */
public class AppUserDetails implements UserDetails {
    private final Long id;
    private final String email;
    private final String passwordHash;

    public AppUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
    }

    public Long getId() { return id; }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public List<GrantedAuthority> getAuthorities() { return List.of(); }
}
