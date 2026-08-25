package my.savingbuddy.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos.AuthUser;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthenticationManager authenticationManager;
    private final CurrentUser currentUser;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService auth, AuthenticationManager authenticationManager, CurrentUser currentUser) {
        this.auth = auth;
        this.authenticationManager = authenticationManager;
        this.currentUser = currentUser;
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
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
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
        if (old != null) old.invalidate();
        request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
