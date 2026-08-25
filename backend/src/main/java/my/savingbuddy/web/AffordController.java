package my.savingbuddy.web;

import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos.*;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.service.AffordabilityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/afford")
public class AffordController {
    private final AffordabilityService afford;
    private final CurrentUser currentUser;

    public AffordController(AffordabilityService afford, CurrentUser currentUser) { this.afford = afford;     this.currentUser = currentUser; }

    @PostMapping("/preview")
    public AffordPreview preview(@Valid @RequestBody AffordRequest request) {
        return afford.preview(currentUser.id(), request.amount());
    }

    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public BuyResponse buy(@Valid @RequestBody AffordRequest request) {
        return afford.buy(currentUser.id(), request.amount());
    }

    @PostMapping("/wait")
    @ResponseStatus(HttpStatus.CREATED)
    public SavingPlanDto wait(@Valid @RequestBody AffordRequest request) {
        return afford.waitAndSave(currentUser.id(), request.amount());
    }
}
