package my.savingbuddy.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A single-use invitation to create an account.
 *
 * <p>Only the token's SHA-256 digest is stored. The plaintext is shown to the
 * inviter once, at creation, and never again — so a database dump does not hand
 * anyone a working invite.
 */
@Entity
@Table(name = "invites")
public class Invite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_user_id")
    private Long usedByUserId;

    protected Invite() {}

    public Invite(String tokenHash, Long createdByUserId, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() { return usedAt != null; }

    public boolean isExpired(Instant now) { return now.isAfter(expiresAt); }

    /** Pending / Used / Expired, for the inviter's own list. */
    public String status(Instant now) {
        if (isUsed()) return "USED";
        return isExpired(now) ? "EXPIRED" : "PENDING";
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Long getUsedByUserId() { return usedByUserId; }
}
