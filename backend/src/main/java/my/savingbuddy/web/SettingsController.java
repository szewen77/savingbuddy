package my.savingbuddy.web;

import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos.SettingsRequest;
import my.savingbuddy.api.Dtos.SettingsResponse;
import my.savingbuddy.service.SettingsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService settings;

    public SettingsController(SettingsService settings) { this.settings = settings; }

    @GetMapping
    public SettingsResponse get() { return settings.get(); }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return settings.update(request);
    }
}
