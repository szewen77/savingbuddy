package my.savingbuddy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Snapshots the database on startup and keeps the most recent few.
 *
 * <p>A local install has exactly one copy of the user's financial history, so a
 * cheap rolling backup is the difference between a bad day and a lost year. Uses
 * H2's own {@code BACKUP TO}, which is safe to run against a live database.
 */
@Component
public class BackupService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final String PREFIX = "savingbuddy-";
    private static final String SUFFIX = ".zip";

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final int keep;
    private final Path directory;

    public BackupService(JdbcTemplate jdbc,
                         @Value("${savingbuddy.backup.enabled:true}") boolean enabled,
                         @Value("${savingbuddy.backup.keep:7}") int keep,
                         @Value("${savingbuddy.backup.dir:${user.home}/.savingbuddy/backups}") String dir) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.keep = Math.max(1, keep);
        this.directory = Path.of(dir);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            snapshot();
        } catch (Exception e) {
            // A failed backup must never stop the app from starting.
            log.warn("Could not write a startup backup: {}", e.getMessage());
        }
    }

    /** Writes a timestamped snapshot and prunes older ones. Returns the file written. */
    public Path snapshot() throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(PREFIX + LocalDateTime.now().format(STAMP) + SUFFIX);
        jdbc.execute("BACKUP TO '" + target.toAbsolutePath() + "'");
        prune();
        log.info("Database backed up to {}", target);
        return target;
    }

    private void prune() throws IOException {
        try (var files = Files.list(directory)) {
            List<Path> snapshots = files
                .filter(p -> p.getFileName().toString().startsWith(PREFIX) && p.getFileName().toString().endsWith(SUFFIX))
                .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                .toList();
            for (Path stale : snapshots.stream().skip(keep).toList()) {
                Files.deleteIfExists(stale);
                log.debug("Pruned old backup {}", stale.getFileName());
            }
        }
    }
}
