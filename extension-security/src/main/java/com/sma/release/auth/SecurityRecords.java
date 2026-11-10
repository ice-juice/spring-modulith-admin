package com.sma.release.auth;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import com.sma.extension.protocol.ActionIntent;

/** Persistence-only records. Never return User, Session, Challenge or Grant as HTTP response bodies. */
public final class SecurityRecords {
    private SecurityRecords() {}
    public record User(String id, String username, String loginHash, String operationHash, String sealedTotp,
            long usedTotpStep, long credentialVersion, boolean enabled, boolean enrolled, boolean securityAdmin,
            String enrollmentTokenHash, Instant enrollmentExpiresAt, List<String> recoveryHashes) {
        public User { recoveryHashes = List.copyOf(recoveryHashes); }
        public User useStep(long step) { return new User(id, username, loginHash, operationHash, sealedTotp, step, credentialVersion, enabled, enrolled, securityAdmin, enrollmentTokenHash, enrollmentExpiresAt, recoveryHashes); }
        public User activate(long step, List<String> recovery) { return new User(id, username, loginHash, operationHash, sealedTotp, step, credentialVersion+1, enabled, true, securityAdmin, null, null, recovery); }
    }
    public record Session(String tokenHash, String id, String userId, long credentialVersion, Instant createdAt, Instant expiresAt) {}
    public record ScopeGrant(String id, String userId, String groupId, String environment, Set<ActionIntent.Action> actions, boolean read) {
        public ScopeGrant { actions = Set.copyOf(actions); }
    }
    public record Challenge(String id, String userId, String sessionId, long credentialVersion, String intentHash,
            ActionIntent intent, Instant expiresAt) {}
    public record Grant(String tokenHash, String userId, String sessionId, long credentialVersion, String intentHash,
            Instant expiresAt, boolean used, String actionId) {}
    public record Throttle(int failures, Instant nextAllowedAt) {}
    public record Principal(String userId, String username, String sessionId, long credentialVersion, boolean securityAdmin) {}
    public record LoginTicket(String token, Instant expiresAt) {}
    public record Enrollment(String secret, String issuer, String account, Instant expiresAt) {}
    public record NewAccount(String userId, String enrollmentToken, Instant expiresAt) {}
    public record ChallengeView(String id, ActionIntent intent, String digest, Instant expiresAt) {}
    public record GrantView(String token, Instant expiresAt) {}
}
