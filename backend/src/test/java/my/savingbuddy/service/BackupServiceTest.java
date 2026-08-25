package my.savingbuddy.service;

import my.savingbuddy.FixedClockConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The backup guard. Losing the database and restarting must not destroy the very
 * backups that could have recovered it.
 *
 * <p>Runs against a file-backed database rather than the in-memory one the other
 * tests use: H2 refuses {@code BACKUP TO} on a non-persistent database, so an
 * in-memory test would prove nothing about the real code path.
 */
@SpringBootTest
@Import(FixedClockConfig.class)
class BackupServiceTest {
    private static final Path DB_DIR =
        Path.of("target", "backup-test", UUID.randomUUID().toString());

    @DynamicPropertySource
    static void fileBackedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:file:" + DB_DIR.toAbsolutePath().resolve("savingbuddy") + ";MODE=PostgreSQL");
        registry.add("savingbuddy.backup.enabled", () -> false); // the runner is driven explicitly below
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (!Files.exists(DB_DIR)) return;
        try (var paths = Files.walk(DB_DIR)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Autowired JdbcTemplate jdbc;

    private BackupService writingTo(Path dir) {
        return new BackupService(jdbc, true, 3, dir.toString());
    }

    private void configureAPlan() {
        jdbc.update("""
            insert into plan (owner_name, employer, payday, salary, bills_allocation, savings_target, spending_allowance)
            values ('Nurul', 'Petronas', 25, 7200, 2100, 3000, 2100)""");
    }

    private long countIn(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    void anEmptyInstallIsNeverSnapshotted(@TempDir Path dir) throws IOException {
        jdbc.update("delete from plan");
        BackupService backups = writingTo(dir);

        assertThat(backups.hasSomethingWorthKeeping()).isFalse();
        backups.run(null);

        // The critical case: after losing the database, restarting must not write
        // an empty snapshot that pushes a real one out of the rotation.
        assertThat(countIn(dir)).isZero();
    }

    @Test
    void aConfiguredInstallIsSnapshotted(@TempDir Path dir) throws IOException {
        jdbc.update("delete from plan");
        configureAPlan();
        BackupService backups = writingTo(dir);

        assertThat(backups.hasSomethingWorthKeeping()).isTrue();
        backups.run(null);

        assertThat(countIn(dir)).isEqualTo(1);
    }

    @Test
    void aSnapshotCanBeRestored(@TempDir Path dir) throws Exception {
        jdbc.update("delete from plan");
        configureAPlan();

        Path snapshot = writingTo(dir).snapshot();

        // A backup is only worth having if it can be read back, so unpack it and
        // open the recovered file as its own database.
        Path restored = dir.resolve("restored");
        Files.createDirectories(restored);
        try (var zip = new java.util.zip.ZipFile(snapshot.toFile())) {
            var entry = zip.entries().nextElement();
            Files.copy(zip.getInputStream(entry), restored.resolve(entry.getName()));
        }

        var ds = org.springframework.jdbc.datasource.DriverManagerDataSource.class
            .getDeclaredConstructor().newInstance();
        ds.setUrl("jdbc:h2:file:" + restored.resolve("savingbuddy") + ";MODE=PostgreSQL;ACCESS_MODE_DATA=r");
        ds.setUsername("sa");
        ds.setPassword("");

        String owner = new JdbcTemplate(ds).queryForObject("select owner_name from plan", String.class);
        assertThat(owner).isEqualTo("Nurul");
    }

    @Test
    void onlyTheMostRecentSnapshotsAreKept(@TempDir Path dir) throws Exception {
        jdbc.update("delete from plan");
        configureAPlan();
        BackupService backups = writingTo(dir);

        for (int i = 0; i < 5; i++) {
            backups.snapshot();
            Thread.sleep(1100); // filenames stamp to the second
        }

        assertThat(countIn(dir)).isEqualTo(3);
    }
}
