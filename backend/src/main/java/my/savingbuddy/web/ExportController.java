package my.savingbuddy.web;

import my.savingbuddy.api.Dtos.ExportBundle;
import my.savingbuddy.security.CurrentUser;
import my.savingbuddy.service.BudgetClock;
import my.savingbuddy.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/export")
public class ExportController {
    private final ExportService exports;
    private final BudgetClock clock;
    private final CurrentUser currentUser;

    public ExportController(ExportService exports, BudgetClock clock, CurrentUser currentUser) {
        this.exports = exports;
        this.clock = clock;
        this.currentUser = currentUser; }

    /** Everything in the database, as a downloadable JSON file. */
    @GetMapping
    public ResponseEntity<ExportBundle> export() {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + ExportService.filename(clock.today()) + "\"")
            .body(exports.export(currentUser.id()));
    }
}
