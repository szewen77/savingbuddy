package my.savingbuddy.web;

import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos.SettingsRequest;
import my.savingbuddy.api.Dtos.SettingsResponse;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.service.SettingsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService settings;
    private final CurrentUser currentUser;

    public SettingsController(SettingsService settings, CurrentUser currentUser) { this.settings = settings;     this.currentUser = currentUser; }

    @GetMapping
    public SettingsResponse get() { return settings.get(currentUser.id()); }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return settings.update(currentUser.id(), request);
    }
}
