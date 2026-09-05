package my.savingbuddy.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A single-use code that lets one account set a new password without knowing the
 * old one.
 *
 * <p>Only the SHA-256 digest is stored; the plaintext is shown to the person who
 * minted it, once. Short-lived by design — an hour rather than an invite's
 * fortnight — because this opens an existing account holding a household's
 * financial history, where an invite only creates an empty one.
 */
@Entity
@Table(name = "password_resets")
public class PasswordReset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordReset() {}

    public PasswordReset(String tokenHash, Long targetUserId, Long createdByUserId,
                         Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.targetUserId = targetUserId;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getTargetUserId() { return targetUserId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
