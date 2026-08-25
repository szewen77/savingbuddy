package my.savingbuddy.service;

import jakarta.annotation.PostConstruct;
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
 *
 * <p>An unconfigured database is never snapshotted. Without that guard, losing the
 * database and restarting would write an empty backup, and because old snapshots
 * are pruned, restarting a few more times would evict every good one — turning a
 * recoverable accident into permanent loss at exactly the worst moment.
 *
 * <p>{@code BACKUP TO} is H2-only. Rather than let it fail into a warning on
 * PostgreSQL — leaving a deployment convinced it has backups it does not have —
 * mode {@code snapshot} refuses to start against any other database. A Postgres
 * deployment must say {@code mode: none}, making "the platform owns durability"
 * an explicit statement rather than an accident.
 */
@Component
public class BackupService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final String PREFIX = "savingbuddy-";
    private static final String SUFFIX = ".zip";

    /** How this deployment gets its durability. */
    public enum Mode {
        /** The app writes its own H2 file snapshots. Requires H2. */
        SNAPSHOT,
        /** The app takes no backups — something else owns durability. */
        NONE
    }

    private final JdbcTemplate jdbc;
    private final Mode mode;
    private final int keep;
    private final Path directory;

    public BackupService(JdbcTemplate jdbc,
                         @Value("${savingbuddy.backup.mode:snapshot}") Mode mode,
                         @Value("${savingbuddy.backup.keep:7}") int keep,
                         @Value("${savingbuddy.backup.dir:${user.home}/.savingbuddy/backups}") String dir) {
        this.jdbc = jdbc;
        this.mode = mode;
        this.keep = Math.max(1, keep);
        this.directory = Path.of(dir);
    }

    /**
     * Fails startup when snapshots are requested against a database that cannot
     * produce them. Checked against the live connection, not the classpath: H2
     * stays on the classpath in a Postgres deployment because tests need it.
     */
    @PostConstruct
    void verifyModeMatchesDatabase() {
        if (mode != Mode.SNAPSHOT) return;
        String product = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c ->
            c.getMetaData().getDatabaseProductName());
        if (!"H2".equalsIgnoreCase(product)) {
            throw new IllegalStateException(
                "savingbuddy.backup.mode=snapshot needs H2 (BACKUP TO is H2-only), but this is " + product
                    + ". Set savingbuddy.backup.mode=none and make sure something else backs this database up.");
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (mode == Mode.NONE) return;
        try {
            if (!hasSomethingWorthKeeping()) {
                log.debug("Nothing configured yet — skipping the startup backup.");
                return;
            }
            snapshot();
        } catch (Exception e) {
            // A failed backup must never stop the app from starting.
            log.warn("Could not write a startup backup: {}", e.getMessage());
        }
    }

    /**
     * True when the database holds a configured plan. Snapshotting an empty
     * database is worse than useless: it consumes a rotation slot and pushes a
     * real backup out.
     */
    boolean hasSomethingWorthKeeping() {
        Integer plans = jdbc.queryForObject("select count(*) from plan", Integer.class);
        return plans != null && plans > 0;
    }

    /** Writes a timestamped snapshot and prunes older ones. Returns the file written. */
    public Path snapshot() throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(PREFIX + LocalDateTime.now().format(STAMP) + SUFFIX);
        // BACKUP TO takes no bind parameters, so the path is inlined. Escape the
        // quote character: a home directory containing an apostrophe would
        // otherwise produce invalid SQL and silently disable every backup.
        String literal = target.toAbsolutePath().toString().replace("'", "''");
        jdbc.execute("BACKUP TO '" + literal + "'");
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
