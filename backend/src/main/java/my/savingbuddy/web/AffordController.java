package my.savingbuddy.web;

import jakarta.validation.Valid;
import my.savingbuddy.api.Dtos.*;
import my.savingbuddy.service.AffordabilityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/afford")
public class AffordController {
    private final AffordabilityService afford;

    public AffordController(AffordabilityService afford) { this.afford = afford; }

    @PostMapping("/preview")
    public AffordPreview preview(@Valid @RequestBody AffordRequest request) {
        return afford.preview(request.amount());
    }

    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public BuyResponse buy(@Valid @RequestBody AffordRequest request) {
        return afford.buy(request.amount());
    }

    @PostMapping("/wait")
    @ResponseStatus(HttpStatus.CREATED)
    public SavingPlanDto wait(@Valid @RequestBody AffordRequest request) {
        return afford.waitAndSave(request.amount());
    }
}
