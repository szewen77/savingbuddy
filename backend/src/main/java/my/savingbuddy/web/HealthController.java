package my.savingbuddy.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness/readiness for the hosting platform. Deployment plumbing, not product
 * surface — no authentication, and nothing about the user's data.
 *
 * <p>It touches the database on purpose: an instance that is up but cannot reach
 * Postgres is not serving anything useful, and a health check that only proves
 * the JVM started would keep such an instance in rotation.
 */
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> health() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            // Deliberately no exception detail: this endpoint is unauthenticated,
            // and a driver error can name hosts, users and schema.
            return ResponseEntity.status(503).body(Map.of("status", "database unavailable"));
        }
    }
}
