package my.savingbuddy.web;

import my.savingbuddy.api.Dtos.InviteDto;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.service.InviteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Invitations.
 *
 * <p>Deliberately NOT in SecurityConfig's permitAll list — it is authenticated by
 * the {@code /api/**} catch-all. An unauthenticated caller here could mint
 * accounts at will.
 */
@RestController
@RequestMapping("/api/invites")
public class InviteController {
    private final InviteService invites;
    private final CurrentUser currentUser;

    public InviteController(InviteService invites, CurrentUser currentUser) {
        this.invites = invites;
        this.currentUser = currentUser;
    }

    /** The caller's own invites. Never anyone else's. */
    @GetMapping
    public List<InviteDto> list() {
        return invites.list(currentUser.id());
    }

    /** The only response that ever carries the plaintext token. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InviteDto create() {
        return invites.create(currentUser.id());
    }
}
