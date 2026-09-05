package my.savingbuddy.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos;
import my.savingbuddy.api.Dtos.AuthUser;
import my.savingbuddy.api.Dtos.ChangePasswordRequest;
import my.savingbuddy.api.Dtos.LoginRequest;
import my.savingbuddy.api.Dtos.RegisterRequest;
import my.savingbuddy.api.Dtos.PasswordResetDto;
import my.savingbuddy.api.Dtos.RedeemResetRequest;
import my.savingbuddy.api.Dtos.RegistrationStatus;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.security.RegistrationPolicy;
import my.savingbuddy.service.RegistrationModeService;
import my.savingbuddy.security.LoginRateLimiter;
import my.savingbuddy.service.AuthService;
import my.savingbuddy.service.PasswordResetService;
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
    private final my.savingbuddy.repository.UserRepository users;
    private final LoginRateLimiter rateLimiter;
    private final RegistrationPolicy registrationPolicy;
    private final PasswordResetService passwordResets;
    private final RegistrationModeService modes;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService auth, AuthenticationManager authenticationManager,
                          CurrentUser currentUser, SessionRegistry sessionRegistry,
                          my.savingbuddy.repository.UserRepository users,
                          LoginRateLimiter rateLimiter, RegistrationPolicy registrationPolicy,
                          RegistrationModeService modes, PasswordResetService passwordResets) {
        this.auth = auth;
        this.authenticationManager = authenticationManager;
        this.currentUser = currentUser;
        this.sessionRegistry = sessionRegistry;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.registrationPolicy = registrationPolicy;
        this.modes = modes;
        this.passwordResets = passwordResets;
    }

    /**
     * Whether this instance accepts new accounts, so the sign-in screen can ask
     * for a code or hide the link. Not sensitive: one registration attempt
     * reveals the same thing.
     */
    @GetMapping("/registration")
    public RegistrationStatus registrationStatus() {
        return new RegistrationStatus(modes.current().name().toLowerCase());
    }

    /**
     * Changes who may register, from the app. Authenticated by the /api/** rule —
     * an anonymous caller must never be able to open the door.
     */
    @PutMapping("/registration")
    public RegistrationStatus setRegistrationMode(@Valid @RequestBody Dtos.RegistrationModeRequest req) {
        RegistrationPolicy.Mode mode;
        try {
            mode = RegistrationPolicy.Mode.valueOf(req.mode().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new my.savingbuddy.service.SetupService.InvalidSetupException(
                "Unknown registration mode: " + req.mode());
        }
        return new RegistrationStatus(modes.set(mode).name().toLowerCase());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUser register(@Valid @RequestBody RegisterRequest req,
                             HttpServletRequest request, HttpServletResponse response) {
        // Registration is throttled on the same budget as login. Without this a
        // signup code could be brute-forced, and the "email already exists"
        // conflict below is an unlimited enumeration oracle.
        String ip = request.getRemoteAddr();
        rateLimiter.checkAllowed(ip, req.email());
        AuthUser created;
        try {
            created = auth.register(req.email(), req.password(), req.signupCode(), req.inviteToken());
        } catch (RuntimeException e) {
            rateLimiter.recordFailure(ip, req.email());
            throw e;
        }
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
    /**
     * Mints a reset code for an account the caller invited. Authenticated by the
     * /api/** catch-all — there is deliberately no public "forgot password"
     * endpoint, which is what removes the account-enumeration oracle entirely.
     */
    @PostMapping("/reset/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PasswordResetDto mintReset(@PathVariable Long userId) {
        return passwordResets.mint(currentUser.id(), userId);
    }

    /**
     * Redeems a code and sets a new password. Public by necessity — the person
     * using it cannot sign in.
     *
     * <p>POST, never GET: chat clients fetch link previews, and a crawler
     * following a GET would burn the code before the human clicked it.
     */
    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redeemReset(@Valid @RequestBody RedeemResetRequest req, HttpServletRequest request) {
        // Throttled explicitly: the limiter is invoked by hand in this class, not
        // by a filter, so a new route inherits nothing. Keyed on IP only — keying
        // on the target's email would let anyone lock a household member out of
        // signing in by failing resets against their address.
        String ip = request.getRemoteAddr();
        rateLimiter.checkAllowed(ip, null);

        Long userId;
        try {
            userId = passwordResets.redeem(req.token(), req.newPassword());
        } catch (RuntimeException e) {
            rateLimiter.recordFailure(ip, null);
            throw e;
        }

        // Every session of that account, with no exception for a "current" one:
        // this route is unauthenticated, and the person resetting may be
        // recovering from someone else holding a session.
        users.findById(userId).ifPresent(user -> {
            Object principal = new my.savingbuddy.security.AppUserDetails(user);
            sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
        });
        rateLimiter.recordSuccess(ip, null);
    }

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
        // Called explicitly rather than via a filter or a failure handler: this
        // controller authenticates by hand, so AuthenticationFailureHandler never
        // fires, and the failure event that does fire carries no client details.
        String ip = request.getRemoteAddr();
        rateLimiter.checkAllowed(ip, email);

        Authentication authentication;
        try {
            authentication =
                authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (org.springframework.security.core.AuthenticationException e) {
            rateLimiter.recordFailure(ip, email);
            throw e;
        }
        rateLimiter.recordSuccess(ip, email);
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
