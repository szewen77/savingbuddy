package my.savingbuddy.web;

import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos.SetupRequest;
import my.savingbuddy.api.Dtos.SetupStatus;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.service.SetupService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/setup")
public class SetupController {
    private final SetupService setup;
    private final CurrentUser currentUser;

    public SetupController(SetupService setup, CurrentUser currentUser) { this.setup = setup;     this.currentUser = currentUser; }

    /** Whether this install has been configured yet. The frontend calls this before anything else. */
    @GetMapping
    public SetupStatus status() { return setup.status(currentUser.id()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SetupStatus configure(@Valid @RequestBody SetupRequest request) {
        return setup.configure(currentUser.id(), request);
    }
}
