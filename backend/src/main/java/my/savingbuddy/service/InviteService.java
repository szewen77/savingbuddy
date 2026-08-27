package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.InviteDto;
import my.savingbuddy.domain.Invite;
import my.savingbuddy.repository.InviteRepository;
import my.savingbuddy.repository.UserRepository;
import my.savingbuddy.security.RegistrationPolicy;
import my.savingbuddy.security.RegistrationPolicy.RegistrationNotAllowedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Single-use invitations, so admitting someone is a product action rather than a redeploy. */
@Service
public class InviteService {

    static final Duration VALID_FOR = Duration.ofDays(14);
    /** Bounds how much one account can write to the table, and how many live doors exist at once. */
    static final int MAX_OPEN_PER_USER = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteRepository invites;
    private final UserRepository users;
    private final Clock clock;

    public InviteService(InviteRepository invites, UserRepository users, Clock clock) {
        this.invites = invites;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Mints an invite. The plaintext token is returned here and nowhere else —
     * only its digest is stored, so it cannot be recovered later.
     */
    @Transactional
    public InviteDto create(Long userId) {
        Instant now = Instant.now(clock);
        if (invites.countByCreatedByUserIdAndUsedAtIsNullAndExpiresAtAfter(userId, now) >= MAX_OPEN_PER_USER) {
            throw new TooManyInvitesException(
                "You already have " + MAX_OPEN_PER_USER + " unused invites. Use or wait out one of those first.");
        }

        // 256 bits: this token is the entire credential, so it must not be guessable.
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Invite invite = invites.save(new Invite(hash(token), userId, now, now.plus(VALID_FOR)));
        return toDto(invite, now, token);
    }

    @Transactional(readOnly = true)
    public List<InviteDto> list(Long userId) {
        Instant now = Instant.now(clock);
        return invites.findAllByCreatedByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(i -> toDto(i, now, null))
            .toList();
    }

    /**
     * Claims an invite for a newly created user, or throws.
     *
     * <p>One message for every failure. Distinguishing "used" from "expired" from
     * "never existed" would confirm to a prober that a token is real; the invitee
     * only needs to know the remedy, which is the same in all three cases.
     */
    @Transactional
    public void claimFor(String token, Long newUserId) {
        if (token == null || token.isBlank() || invites.claim(hash(token), newUserId, Instant.now(clock)) != 1) {
            throw new RegistrationNotAllowedException(
                "That invite isn't valid — it may have been used already, or expired. Ask for a new one.");
        }
    }

    private InviteDto toDto(Invite i, Instant now, String plaintextOnce) {
        String usedBy = i.getUsedByUserId() == null ? null
            : users.findById(i.getUsedByUserId()).map(u -> u.getEmail()).orElse(null);
        return new InviteDto(i.getId(), plaintextOnce, i.status(now), i.getCreatedAt(), i.getExpiresAt(), usedBy);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static class TooManyInvitesException extends RuntimeException {
        public TooManyInvitesException(String m) { super(m); }
    }
}
