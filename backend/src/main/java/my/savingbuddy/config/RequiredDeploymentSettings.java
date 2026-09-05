package my.savingbuddy.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the deployment's required settings before anything tries to use them.
 *
 * <p>Without this, a missing DATABASE_URL surfaces as HikariCP's
 * {@code 'url' must start with jdbc}, several stack frames deep and naming
 * neither the variable nor the fix. An operator reading deploy logs should be
 * told which environment variable is missing, once, in the first error.
 *
 * <p>Runs as an EnvironmentPostProcessor so it fires after profiles resolve but
 * before the datasource, Flyway or any other bean is built.
 */
public class RequiredDeploymentSettings implements EnvironmentPostProcessor {

    /** Only meaningful for a real deployment; the local H2 default needs none of these. */
    private static final String PROFILE = "postgres";

    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();
    static {
        REQUIRED.put("DATABASE_URL",
            "the JDBC URL, e.g. jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require");
        REQUIRED.put("DATABASE_USERNAME", "the database user, e.g. postgres.<project-ref> for a Supabase session pooler");
        REQUIRED.put("DATABASE_PASSWORD", "the database password");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!List.of(env.getActiveProfiles()).contains(PROFILE)) return;

        List<String> missing = new ArrayList<>();
        REQUIRED.forEach((key, hint) -> {
            String value = env.getProperty(key);
            if (value == null || value.isBlank()) missing.add("  " + key + " — " + hint);
        });

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Profile '" + PROFILE + "' is active but these environment variables are missing:\n"
                    + String.join("\n", missing)
                    + "\nThere is deliberately no localhost fallback: a misconfigured deployment must fail,"
                    + " not quietly start against the wrong database.");
        }

        checkUrlShape(env.getProperty("DATABASE_URL"));
    }

    /**
     * Catches the wrong copy-paste before HikariCP does.
     *
     * <p>A hosted Postgres dashboard offers several connection strings, and only
     * one of them is a JDBC URL. Pasting the libpq URI produces
     * {@code 'url' must start with "jdbc"} from four frames inside the datasource
     * factory, which names neither the variable nor the fix.
     */
    private void checkUrlShape(String url) {
        if (url == null || url.isBlank() || url.startsWith("jdbc:")) return;

        String hint = url.startsWith("postgres://") || url.startsWith("postgresql://")
            ? "That looks like the libqp/URI form. Use the JDBC one instead — same host, prefixed with"
              + " 'jdbc:' — and drop any user:password@ from it, since DATABASE_USERNAME and"
              + " DATABASE_PASSWORD are passed separately."
            : "A JDBC URL starts with 'jdbc:'.";

        // Print only the scheme: the URI form usually carries the password.
        String scheme = url.contains("://") ? url.substring(0, url.indexOf("://") + 3) : "(no scheme)";
        throw new IllegalStateException(
            "DATABASE_URL must be a JDBC URL, but it starts with '" + scheme + "'. " + hint
                + "\nExpected shape: jdbc:postgresql://<host>:5432/postgres?sslmode=require&currentSchema=savingbuddy");
    }
}
