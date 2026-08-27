package my.savingbuddy.service;

import my.savingbuddy.domain.AppSettings;
import my.savingbuddy.repository.AppSettingsRepository;
import my.savingbuddy.security.RegistrationPolicy;
import my.savingbuddy.security.RegistrationPolicy.Mode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/**
 * The live registration mode, owned by the database rather than the environment.
 *
 * <p>Changing who may register used to mean editing a host environment variable
 * and waiting for a redeploy. It is a product decision, so it belongs in the
 * product. The environment variable still supplies the value when no row exists,
 * which keeps a fresh deployment fail-closed and keeps local development on its
 * loopback-only {@code open} default.
 */
@Service
public class RegistrationModeService {

    /**
     * What the UI may select. {@code OPEN} is excluded on purpose: it is only
     * safe for a loopback-bound local instance, and a deployed UI must not be
     * one click away from admitting the whole internet. {@code CODE} is excluded
     * because its secret lives in the environment — selecting it from the app
     * could not supply one.
     */
    public static final Set<Mode> SELECTABLE = Set.of(Mode.CLOSED, Mode.INVITE);

    private final AppSettingsRepository settings;
    private final RegistrationPolicy envPolicy;
    private final Clock clock;

    public RegistrationModeService(AppSettingsRepository settings, RegistrationPolicy envPolicy, Clock clock) {
        this.settings = settings;
        this.envPolicy = envPolicy;
        this.clock = clock;
    }

    /** The stored mode, or the environment's if nothing has been chosen yet. */
    @Transactional(readOnly = true)
    public Mode current() {
        return settings.findById(AppSettings.SINGLETON_ID)
            .map(AppSettings::getRegistrationMode)
            .orElseGet(envPolicy::mode);
    }

    @Transactional
    public Mode set(Mode mode) {
        if (!SELECTABLE.contains(mode)) {
            throw new SetupService.InvalidSetupException(
                "Registration can only be set to closed or invite from the app. "
                    + "Modes that depend on an environment secret are configured on the host.");
        }
        Instant now = Instant.now(clock);
        AppSettings row = settings.findById(AppSettings.SINGLETON_ID).orElse(null);
        if (row == null) {
            settings.save(new AppSettings(mode, now));
        } else {
            row.setRegistrationMode(mode, now);
        }
        return mode;
    }
}
