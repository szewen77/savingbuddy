package my.savingbuddy.web;

import my.savingbuddy.api.Dtos.SummaryResponse;
import my.savingbuddy.service.BudgetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {
    private final BudgetService budget;

    public SummaryController(BudgetService budget) { this.budget = budget; }

    @GetMapping
    public SummaryResponse summary() { return budget.summary(); }
}
