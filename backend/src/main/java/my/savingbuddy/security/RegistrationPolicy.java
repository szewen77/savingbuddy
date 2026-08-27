package my.savingbuddy.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Who is allowed to create an account on this instance.
 *
 * <p>Fails closed. An unset mode means CLOSED, never open — the two failure costs
 * are wildly asymmetric and one of them is silent. A wrongly-open deployment
 * hands strangers accounts inside someone's financial history and announces
 * nothing; a wrongly-closed one costs a restart and complains the first time
 * anyone tries to sign up.
 *
 * <p>There is deliberately no "open while the users table is empty" bootstrap
 * exemption. That is a race, not a control: between the deployment answering its
 * first request and the owner registering, whoever finds the URL becomes user #1
 * — and user #1 owns the instance.
 */
@Component
public class RegistrationPolicy {

    public enum Mode {
        /** Anyone may register. Only safe when the app is not reachable from a network. */
        OPEN,
        /** A shared signup code is required. */
        CODE,
        /**
         * A single-use invite, created in-app by an existing user. The env code
         * stops working. Cannot bootstrap an empty database — no users means no
         * invites — so CODE remains the default for a fresh deployment.
         */
        INVITE,
        /** No new accounts. */
        CLOSED
    }

    /** Short codes are guessable, and a gate that can be brute-forced is not a gate. */
    static final int MIN_CODE_LENGTH = 16;

    private final Mode mode;
    private final String code;

    public RegistrationPolicy(@Value("${savingbuddy.registration.mode:closed}") Mode mode,
                              @Value("${savingbuddy.registration.code:}") String code) {
        this.mode = mode;
        this.code = code == null ? "" : code.trim();
    }

    /**
     * Refuses to start when the operator asked for a code gate and supplied no
     * usable code — the same precedent as backup mode: believing registration is
     * gated when it is not is worse than knowing it is open.
     *
     * <p>Narrow by design: it fires only on that one inconsistency, so it can
     * never take down an instance whose existing users just want to sign in.
     */
    @PostConstruct
    void verifyConfigured() {
        if (mode == Mode.CODE && code.length() < MIN_CODE_LENGTH) {
            throw new IllegalStateException(
                "savingbuddy.registration.mode=code needs savingbuddy.registration.code set to at least "
                    + MIN_CODE_LENGTH + " characters (set REGISTRATION_CODE). "
                    + "Use mode=closed if you meant to allow no new accounts.");
        }
    }

    public Mode mode() { return mode; }

    /**
     * Throws unless this caller may create an account with the shared code.
     * INVITE mode is settled by InviteService instead, since it needs the database.
     */
    public void check(String submittedCode) {
        switch (mode) {
            case OPEN -> { }
            case CLOSED -> throw new RegistrationNotAllowedException("Registration is closed on this instance.");
            case INVITE -> { /* handled by the caller against the invites table */ }
            case CODE -> {
                // One message for wrong, blank and absent alike: distinguishing
                // them would confirm to a prober that a code gate is the only
                // thing between them and an account.
                if (!matches(submittedCode)) {
                    throw new RegistrationNotAllowedException("That signup code isn't valid.");
                }
            }
        }
    }

    /** Constant-time: a length-or-prefix-sensitive compare leaks the code a character at a time. */
    private boolean matches(String submitted) {
        if (submitted == null) return false;
        return MessageDigest.isEqual(
            submitted.trim().getBytes(StandardCharsets.UTF_8),
            code.getBytes(StandardCharsets.UTF_8));
    }

    public static class RegistrationNotAllowedException extends RuntimeException {
        public RegistrationNotAllowedException(String m) { super(m); }
    }
}
