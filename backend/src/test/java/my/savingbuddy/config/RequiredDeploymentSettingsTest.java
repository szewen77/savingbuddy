package my.savingbuddy.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The startup guards. Each exists because the alternative is a stack trace that
 * names neither the variable nor the fix.
 */
class RequiredDeploymentSettingsTest {

    private final RequiredDeploymentSettings settings = new RequiredDeploymentSettings();

    private MockEnvironment deployment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("postgres");
        env.setProperty("DATABASE_URL", "jdbc:postgresql://host:5432/postgres?currentSchema=savingbuddy");
        env.setProperty("DATABASE_USERNAME", "postgres.ref");
        env.setProperty("DATABASE_PASSWORD", "secret");
        return env;
    }

    @Test
    void acceptsAProperlyConfiguredDeployment() {
        assertThatCode(() -> settings.postProcessEnvironment(deployment(), null)).doesNotThrowAnyException();
    }

    @Test
    void namesEveryMissingVariable() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("postgres");
        assertThatThrownBy(() -> settings.postProcessEnvironment(env, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DATABASE_URL")
            .hasMessageContaining("DATABASE_USERNAME")
            .hasMessageContaining("DATABASE_PASSWORD");
    }

    @Test
    void rejectsTheLibpqUriThatDashboardsOfferFirst() {
        // The real mistake: a Connect modal shows a URI tab and a JDBC tab.
        // Pasting the URI produced 'url must start with "jdbc"' from deep inside
        // HikariCP, naming neither the variable nor the remedy.
        MockEnvironment env = deployment();
        env.setProperty("DATABASE_URL", "postgresql://user:pw@host:5432/postgres");
        assertThatThrownBy(() -> settings.postProcessEnvironment(env, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be a JDBC URL")
            .hasMessageContaining("jdbc:postgresql://");
    }

    @Test
    void neverEchoesTheUrlItRejects() {
        // The URI form usually carries the password, and this message lands in
        // deploy logs.
        MockEnvironment env = deployment();
        env.setProperty("DATABASE_URL", "postgresql://user:hunter2-the-password@host:5432/postgres");
        assertThatThrownBy(() -> settings.postProcessEnvironment(env, null))
            .hasMessageNotContaining("hunter2-the-password")
            .hasMessageNotContaining("host:5432");
    }

    @Test
    void ignoresEverythingOutsideTheDeploymentProfile() {
        // The local H2 default needs none of these and must not be second-guessed.
        assertThatCode(() -> settings.postProcessEnvironment(new MockEnvironment(), null)).doesNotThrowAnyException();
    }
}
