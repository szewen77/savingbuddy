package my.savingbuddy.repository;

import my.savingbuddy.domain.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

    /** Reads the row so the target can be identified; the claim below is what makes it single-use. */
    Optional<PasswordReset> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);

    /**
     * Claims atomically. A read-then-write would let two requests racing on the
     * same code both succeed, and the loser would still have set a password.
     * Returns 1 when this caller claimed it.
     */
    @Modifying
    @Query("update PasswordReset r set r.usedAt = :now where r.id = :id and r.usedAt is null and r.expiresAt > :now")
    int claim(@Param("id") Long id, @Param("now") Instant now);

    /** Minting a new code retires any outstanding one for the same account. */
    @Modifying
    @Query("update PasswordReset r set r.usedAt = :now where r.targetUserId = :userId and r.usedAt is null")
    int supersedeOutstanding(@Param("userId") Long userId, @Param("now") Instant now);
}
