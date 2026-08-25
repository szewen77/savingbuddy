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

    /**
     * Identity is the user id, not the object.
     *
     * <p>SessionRegistryImpl keys its principal→sessions map by the principal
     * object itself. Without these, each login files sessions under a key nothing
     * can look up again, and "expire this user's other sessions" silently expires
     * nothing.
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof AppUserDetails other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public List<GrantedAuthority> getAuthorities() { return List.of(); }
}
