package my.savingbuddy.service;

import my.savingbuddy.api.Dtos.PasswordResetDto;
import my.savingbuddy.domain.PasswordReset;
import my.savingbuddy.domain.User;
import my.savingbuddy.repository.InviteRepository;
import my.savingbuddy.repository.PasswordResetRepository;
import my.savingbuddy.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Password reset, mediated by whoever invited the account.
 *
 * <p>There is no public "forgot password" endpoint, and that is the point: such
 * an endpoint takes an arbitrary email and reveals, by its behaviour, whether an
 * account exists. Removing it removes the oracle rather than trying to make it
 * indistinguishable.
 */
@Service
public class PasswordResetService {

    /** An hour, not an invite's fortnight: this opens an account that already holds data. */
    static final Duration VALID_FOR = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetRepository resets;
    private final InviteRepository invites;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PasswordResetService(PasswordResetRepository resets, InviteRepository invites, UserRepository users,
                                PasswordEncoder passwordEncoder, Clock clock) {
        this.resets = resets;
        this.invites = invites;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Mints a code for an account the caller invited.
     *
     * <p>Whoever could let you in can let you back in — the power to admit and to
     * re-admit are the same power, so this grants no new authority. Notably it
     * does NOT allow minting for yourself: that would set a new password without
     * knowing the old one, bypassing the current-password check on
     * {@code /api/auth/password}, and turn any open session into a takeover.
     */
    @Transactional
    public PasswordResetDto mint(Long callerId, Long targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new NotAllowedException("Use Change password for your own account.");
        }
        if (!invites.existsByCreatedByUserIdAndUsedByUserId(callerId, targetUserId)) {
            throw new NotAllowedException("You can only reset an account you invited.");
        }
        User target = users.findById(targetUserId)
            .orElseThrow(() -> new NotFoundException("User " + targetUserId + " not found"));

        Instant now = Instant.now(clock);
        // A newly minted code retires any earlier one, so a forwarded old message
        // stops working the moment a replacement is issued.
        resets.supersedeOutstanding(targetUserId, now);

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        resets.save(new PasswordReset(InviteService.hash(token), targetUserId, callerId, now, now.plus(VALID_FOR)));
        return new PasswordResetDto(target.getEmail(), token, now.plus(VALID_FOR));
    }

    /**
     * Consumes a code and sets the new password. Returns the account it belonged
     * to, so the caller can evict that user's sessions.
     *
     * <p>One message for every failure — wrong, expired, already used, or never
     * real. Distinguishing them would confirm a code is genuine.
     */
    @Transactional
    public Long redeem(String token, String newPassword) {
        Instant now = Instant.now(clock);
        PasswordReset reset = (token == null || token.isBlank() ? java.util.Optional.<PasswordReset>empty()
            : resets.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(InviteService.hash(token), now))
            .orElseThrow(() -> new NotAllowedException(
                "That reset code isn't valid — it may have been used already, or expired. Ask for a new one."));

        if (resets.claim(reset.getId(), now) != 1) {
            throw new NotAllowedException(
                "That reset code isn't valid — it may have been used already, or expired. Ask for a new one.");
        }

        User user = users.findById(reset.getTargetUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
        user.changePassword(passwordEncoder.encode(newPassword));
        return user.getId();
    }

    public static class NotAllowedException extends RuntimeException {
        public NotAllowedException(String m) { super(m); }
    }
}
