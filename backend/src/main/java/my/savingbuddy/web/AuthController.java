package my.savingbuddy.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos.AuthUser;
import my.savingbuddy.api.Dtos.ChangePasswordRequest;
import my.savingbuddy.api.Dtos.LoginRequest;
import my.savingbuddy.api.Dtos.RegisterRequest;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthenticationManager authenticationManager;
    private final CurrentUser currentUser;
    private final SessionRegistry sessionRegistry;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService auth, AuthenticationManager authenticationManager,
                          CurrentUser currentUser, SessionRegistry sessionRegistry) {
        this.auth = auth;
        this.authenticationManager = authenticationManager;
        this.currentUser = currentUser;
        this.sessionRegistry = sessionRegistry;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUser register(@Valid @RequestBody RegisterRequest req,
                             HttpServletRequest request, HttpServletResponse response) {
        AuthUser created = auth.register(req.email(), req.password());
        // Registering is also logging in — nobody wants to type the password twice.
        establishSession(req.email().trim().toLowerCase(), req.password(), request, response);
        return created;
    }

    @PostMapping("/login")
    public AuthUser login(@Valid @RequestBody LoginRequest req,
                          HttpServletRequest request, HttpServletResponse response) {
        establishSession(req.email().trim().toLowerCase(), req.password(), request, response);
        return new AuthUser(currentUser.email());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionRegistry.removeSessionInformation(session.getId());
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * Change the password, then evict every OTHER session this user has.
     *
     * <p>Evicting the others is the point: if someone else is riding a stolen
     * session, changing the password has to end it, or the change is theatre.
     * The caller's own session survives so they are not signed out mid-flow.
     */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpServletRequest request) {
        auth.changePassword(currentUser.id(), req.currentPassword(), req.newPassword());

        String keep = request.getSession(false) == null ? null : request.getSession(false).getId();
        for (SessionInformation info : sessionRegistry.getAllSessions(currentUser.principal(), false)) {
            if (!info.getSessionId().equals(keep)) info.expireNow();
        }
    }

    /** Who am I? 401 (from the security filter) when nobody is logged in. */
    @GetMapping("/me")
    public AuthUser me() {
        return new AuthUser(currentUser.email());
    }

    private void establishSession(String email, String password,
                                  HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication =
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        // Session fixation defence: a fresh session id once authenticated.
        HttpSession old = request.getSession(false);
        if (old != null) {
            sessionRegistry.removeSessionInformation(old.getId());
            old.invalidate();
        }
        HttpSession fresh = request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        // Register explicitly. Spring normally does this via the session
        // authentication strategy on the form-login filter; this controller
        // authenticates by hand, so nothing else would record the session and
        // expiring "all other sessions" on password change would silently be a
        // no-op against an empty registry.
        sessionRegistry.registerNewSession(fresh.getId(), authentication.getPrincipal());
    }
}
