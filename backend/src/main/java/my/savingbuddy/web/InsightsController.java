package my.savingbuddy.web;

import my.savingbuddy.api.Dtos.InsightsResponse;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.service.InsightsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {
    private final InsightsService insights;
    private final CurrentUser currentUser;

    public InsightsController(InsightsService insights, CurrentUser currentUser) { this.insights = insights;     this.currentUser = currentUser; }

    @GetMapping
    public InsightsResponse insights() { return insights.insights(currentUser.id()); }
}
