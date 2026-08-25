package my.savingbuddy.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user for the current request.
 *
 * <p>This is the only source of a user id in the request path. Controllers call
 * {@code id()} and pass it down; services take it as a parameter. A client-sent
 * userId is never read anywhere — the frontend does not get to decide whose
 * data it can access.
 */
@Component
public class CurrentUser {
    public Long id() {
        return details().getId();
    }

    public String email() {
        return details().getUsername();
    }

    /** The principal object, as the session registry keys it. */
    public Object principal() {
        return details();
    }

    private AppUserDetails details() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails d)) {
            throw new IllegalStateException("No authenticated user in this request");
        }
        return d;
    }
}
