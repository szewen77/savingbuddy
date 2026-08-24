package my.savingbuddy.web;

import my.savingbuddy.api.Dtos.InsightsResponse;
import my.savingbuddy.service.InsightsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {
    private final InsightsService insights;

    public InsightsController(InsightsService insights) { this.insights = insights; }

    @GetMapping
    public InsightsResponse insights() { return insights.insights(); }
}
