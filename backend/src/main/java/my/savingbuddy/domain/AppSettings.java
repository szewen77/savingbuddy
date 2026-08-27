package my.savingbuddy.domain;

import jakarta.persistence.*;
import my.savingbuddy.security.RegistrationPolicy.Mode;

import java.time.Instant;

/** Instance-wide settings the owner controls from the app rather than the host. */
@Entity
@Table(name = "app_settings")
public class AppSettings {
    /** Single row: there is one instance, and its id is fixed by a check constraint. */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_mode", nullable = false, length = 16)
    private Mode registrationMode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppSettings() {}

    public AppSettings(Mode registrationMode, Instant updatedAt) {
        this.id = SINGLETON_ID;
        this.registrationMode = registrationMode;
        this.updatedAt = updatedAt;
    }

    public void setRegistrationMode(Mode mode, Instant now) {
        this.registrationMode = mode;
        this.updatedAt = now;
    }

    public Mode getRegistrationMode() { return registrationMode; }
    public Instant getUpdatedAt() { return updatedAt; }
}
