package my.savingbuddy.repository;

import my.savingbuddy.domain.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InviteRepository extends JpaRepository<Invite, Long> {

    List<Invite> findAllByCreatedByUserIdOrderByCreatedAtDesc(Long userId);

    int countByCreatedByUserIdAndUsedAtIsNullAndExpiresAtAfter(Long userId, Instant now);

    /**
     * Claims an invite atomically.
     *
     * <p>A conditional UPDATE rather than read-then-write: two registrations
     * racing on the same token would both pass a read check, and the loser would
     * still get an account. Returning 1 means this caller claimed it; 0 means it
     * was already used, expired, or never existed.
     */
    @Modifying
    @Query("""
        update Invite i
           set i.usedAt = :now, i.usedByUserId = :userId
         where i.tokenHash = :tokenHash
           and i.usedAt is null
           and i.expiresAt > :now
        """)
    int claim(@Param("tokenHash") String tokenHash, @Param("userId") Long userId, @Param("now") Instant now);
}
