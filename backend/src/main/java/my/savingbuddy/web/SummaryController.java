package my.savingbuddy.web;

import my.savingbuddy.api.Dtos.SummaryResponse;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.service.BudgetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {
    private final BudgetService budget;
    private final CurrentUser currentUser;

    public SummaryController(BudgetService budget, CurrentUser currentUser) { this.budget = budget;     this.currentUser = currentUser; }

    @GetMapping
    public SummaryResponse summary() { return budget.summary(currentUser.id()); }
}
