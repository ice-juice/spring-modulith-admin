package com.sma.release.auth;

import static com.sma.release.auth.SecurityRecords.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.ActionIntent;
import com.sma.extension.protocol.Checks;
import com.sma.extension.protocol.Hashes;
import com.sma.extension.protocol.ProtocolException;
import com.sma.extension.protocol.ProtocolJson;
import com.sma.release.store.ReleaseDatabase;

@Service
public class IdentityService {
    private static final String EMPTY_HASH = Hashes.sha256(new byte[0]);
    private final ReleaseDatabase database;
    private final CredentialService credentials;
    public IdentityService(ReleaseDatabase database, CredentialService credentials) { this.database = database; this.credentials = credentials; }

    /** Called by one-time local bootstrap, never exposed as an HTTP administrator-creation endpoint. */
    public NewAccount bootstrap(String username, String loginPassword, String operationPassword) {
        return database.transaction(tx -> {
            Checks.require(tx.list("user", User.class).isEmpty(), "bootstrap-already-completed");
            return createAccount(tx, "local-bootstrap", username, loginPassword, operationPassword, true);
        });
    }
    public NewAccount createAccount(ReleaseDatabase.Tx tx, String actor, String username, String loginPassword, String operationPassword, boolean admin) {
        Checks.id(username); Checks.require(!loginPassword.equals(operationPassword), "operation-password-must-differ");
        Checks.require(tx.list("user", User.class).stream().noneMatch(u -> u.username().equals(username)), "username-exists");
        String id = UUID.randomUUID().toString(), token = credentials.randomToken();
        var expires = tx.now().plus(Duration.ofMinutes(15));
        var user = new User(id, username, credentials.passwordHash(loginPassword), credentials.passwordHash(operationPassword),
                credentials.newSealedTotp(id), -1, 1, true, false, admin, credentials.tokenHash(token), expires, List.of());
        tx.put("user", id, user); tx.audit(actor, "user-create", id, "enrollment-required", EMPTY_HASH);
        return new NewAccount(id, token, expires);
    }
    public Enrollment beginEnrollment(String username, String loginPassword, String enrollmentToken, String source) {
        return resolve(database.transaction(tx -> {
            String throttle = throttleKey(username, source); if (!mayAttempt(tx, throttle)) return Attempt.<Enrollment>error("authentication-throttled");
            var user = userByName(tx, username);
            if (!credentials.passwordMatches(loginPassword, user == null ? null : user.loginHash()) || user == null || !user.enabled()
                    || user.enrolled() || user.enrollmentExpiresAt() == null || !tx.now().isBefore(user.enrollmentExpiresAt())
                    || !safeTokenMatch(enrollmentToken, user.enrollmentTokenHash())) return failed(tx, throttle, "enrollment", username);
            // One display only. The confirmation token is kept separately and cannot redisplay the seed.
            if (tx.get("enrollment-shown", user.id(), Boolean.class).orElse(false)) return Attempt.<Enrollment>error("enrollment-already-shown");
            tx.put("enrollment-shown", user.id(), true);
            tx.audit(user.id(), "enrollment-begin", user.id(), "displayed-once", EMPTY_HASH);
            return Attempt.ok(new Enrollment(credentials.totpSecret(user.id(), user.sealedTotp()), "SMA Release", user.username(), user.enrollmentExpiresAt()));
        }));
    }
    public List<String> completeEnrollment(String username, String loginPassword, String enrollmentToken, String code, String source) {
        return resolve(database.transaction(tx -> {
            String throttle = throttleKey(username, source); if (!mayAttempt(tx, throttle)) return Attempt.<List<String>>error("authentication-throttled");
            var user = userByName(tx, username);
            if (!credentials.passwordMatches(loginPassword, user == null ? null : user.loginHash()) || user == null || !user.enabled() || user.enrolled()
                    || user.enrollmentExpiresAt() == null || !tx.now().isBefore(user.enrollmentExpiresAt()) || !safeTokenMatch(enrollmentToken, user.enrollmentTokenHash())
                    || !tx.get("enrollment-shown", user.id(), Boolean.class).orElse(false)) return failed(tx, throttle, "enrollment", username);
            long step = credentials.validUnusedTotpStep(user, code, tx.now());
            if (step < 0) return failed(tx, throttle, "enrollment", username);
            var recovery = credentials.recoveryCodes();
            tx.put("user", user.id(), user.activate(step, recovery.stream().map(credentials::tokenHash).toList()));
            tx.delete("throttle", throttle); tx.delete("enrollment-shown", user.id());
            tx.audit(user.id(), "enrollment-complete", user.id(), "success", EMPTY_HASH);
            return Attempt.ok(recovery);
        }));
    }
    public LoginTicket login(String username, String password, String code, String source) {
        return resolve(database.transaction(tx -> {
            String throttle = throttleKey(username, source); if (!mayAttempt(tx, throttle)) return Attempt.<LoginTicket>error("authentication-throttled");
            var user = userByName(tx, username);
            if (!credentials.passwordMatches(password, user == null ? null : user.loginHash()) || user == null || !user.enabled() || !user.enrolled())
                return failed(tx, throttle, "login", username);
            long step = credentials.validUnusedTotpStep(user, code, tx.now());
            if (step < 0) return failed(tx, throttle, "login", username);
            tx.put("user", user.id(), user.useStep(step));
            String token = credentials.randomToken(), hash = credentials.tokenHash(token);
            var expires = tx.now().plus(Duration.ofHours(1));
            tx.put("session", hash, new Session(hash, UUID.randomUUID().toString(), user.id(), user.credentialVersion(), tx.now(), expires));
            tx.delete("throttle", throttle); tx.audit(user.id(), "login", user.id(), "success", EMPTY_HASH);
            return Attempt.ok(new LoginTicket(token, expires));
        }));
    }
    public Principal principal(String token) { return database.transaction(tx -> principal(tx, token)); }
    public Principal principal(ReleaseDatabase.Tx tx, String token) {
        String hash = credentials.tokenHash(token);
        var session = tx.get("session", hash, Session.class).orElseThrow(() -> new ProtocolException("unauthenticated"));
        var user = tx.get("user", session.userId(), User.class).orElseThrow(() -> new ProtocolException("unauthenticated"));
        Checks.require(user.enabled() && user.enrolled() && user.credentialVersion() == session.credentialVersion()
                && tx.now().isBefore(session.expiresAt()), "unauthenticated");
        return new Principal(user.id(), user.username(), session.id(), user.credentialVersion(), user.securityAdmin());
    }
    public void logout(String token) {
        database.transaction(tx -> { var p = principal(tx, token); tx.delete("session", credentials.tokenHash(token)); tx.audit(p.userId(), "logout", p.sessionId(), "success", EMPTY_HASH); return null; });
    }
    public void requireScope(ReleaseDatabase.Tx tx, Principal principal, ActionIntent intent) {
        if (principal.securityAdmin()) return;
        boolean allowed = tx.list("scope", ScopeGrant.class).stream().anyMatch(g -> g.userId().equals(principal.userId())
                && g.groupId().equals(intent.groupId()) && g.environment().equals(intent.environment()) && g.actions().contains(intent.action()));
        Checks.require(allowed, "forbidden");
    }
    public ChallengeView challenge(String session, ActionIntent intent) {
        return database.transaction(tx -> {
            var p = principal(tx, session); requireScope(tx, p, intent);
            long pending = tx.list("challenge", Challenge.class).stream().filter(c -> c.userId().equals(p.userId()) && tx.now().isBefore(c.expiresAt())).count();
            Checks.require(pending < 10, "too-many-challenges");
            String id = UUID.randomUUID().toString(); var expires = tx.now().plusSeconds(120);
            tx.put("challenge", id, new Challenge(id, p.userId(), p.sessionId(), p.credentialVersion(), intent.digest(), intent, expires));
            tx.audit(p.userId(), "challenge", id, "created", intent.digest());
            return new ChallengeView(id, intent, intent.digest(), expires);
        });
    }
    public GrantView authorize(String session, String challengeId, String operationPassword, String code) {
        return resolve(database.transaction(tx -> {
            var p = principal(tx, session); String throttle = "operation:" + p.userId();
            if (!mayAttempt(tx, throttle)) return Attempt.<GrantView>error("authentication-throttled");
            var c = tx.get("challenge", challengeId, Challenge.class).orElseThrow(() -> new ProtocolException("challenge-unavailable"));
            Checks.require(c.userId().equals(p.userId()) && c.sessionId().equals(p.sessionId()) && c.credentialVersion() == p.credentialVersion()
                    && tx.now().isBefore(c.expiresAt()), "challenge-unavailable");
            requireScope(tx, p, c.intent());
            var user = tx.get("user", p.userId(), User.class).orElseThrow();
            boolean passwordValid = credentials.passwordMatches(operationPassword, user.operationHash());
            long step = passwordValid ? credentials.validUnusedTotpStep(user, code, tx.now()) : -1;
            if (step < 0) return failed(tx, throttle, "operation-verify", p.userId());
            tx.put("user", user.id(), user.useStep(step)); tx.delete("challenge", challengeId); tx.delete("throttle", throttle);
            String token = credentials.randomToken(), hash = credentials.tokenHash(token); var expires = tx.now().plusSeconds(60);
            tx.put("grant", hash, new Grant(hash, p.userId(), p.sessionId(), p.credentialVersion(), c.intentHash(), expires, false, null));
            tx.audit(p.userId(), "operation-verify", challengeId, "success", c.intentHash());
            return Attempt.ok(new GrantView(token, expires));
        }));
    }
    /** Call inside the same database transaction as the accepted command. Replays query action status instead. */
    public Principal consume(ReleaseDatabase.Tx tx, String session, String grantToken, ActionIntent intent, String actionId) {
        var p = principal(tx, session); requireScope(tx, p, intent);
        String hash = credentials.tokenHash(grantToken);
        var grant = tx.get("grant", hash, Grant.class).orElseThrow(() -> new ProtocolException("grant-unavailable"));
        Checks.require(!grant.used() && grant.userId().equals(p.userId()) && grant.sessionId().equals(p.sessionId())
                && grant.credentialVersion() == p.credentialVersion() && grant.intentHash().equals(intent.digest())
                && tx.now().isBefore(grant.expiresAt()), "grant-unavailable");
        tx.put("grant", hash, new Grant(hash, grant.userId(), grant.sessionId(), grant.credentialVersion(), grant.intentHash(), grant.expiresAt(), true, actionId));
        tx.audit(p.userId(), intent.action().name(), actionId, "accepted", intent.digest());
        return p;
    }
    private User userByName(ReleaseDatabase.Tx tx, String username) { return tx.list("user", User.class).stream().filter(u -> u.username().equals(username)).findFirst().orElse(null); }
    private boolean safeTokenMatch(String token, String expected) { try { return expected != null && Hashes.equalHex(credentials.tokenHash(token), expected); } catch (RuntimeException ignored) { return false; } }
    private static String throttleKey(String username, String source) {
        // User scope prevents rotating IPs bypassing account throttling; perimeter rate limit also protects source spray.
        String value = username == null ? "" : username;
        return "login:" + Hashes.sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static boolean mayAttempt(ReleaseDatabase.Tx tx, String key) {
        return tx.get("throttle", key, Throttle.class).map(t -> !tx.now().isBefore(t.nextAllowedAt())).orElse(true);
    }
    private static <T> Attempt<T> failed(ReleaseDatabase.Tx tx, String key, String action, String resource) {
        int failures = tx.get("throttle", key, Throttle.class).map(t -> Math.min(t.failures()+1, 30)).orElse(1);
        long delay = failures < 5 ? 0 : Math.min(300, 5L << Math.min(failures-5, 6));
        tx.put("throttle", key, new Throttle(failures, tx.now().plusSeconds(delay)));
        tx.audit("anonymous", action, "authentication", "denied", EMPTY_HASH);
        return Attempt.error("authentication-failed");
    }
    private record Attempt<T>(T value, String error) {
        static <T> Attempt<T> ok(T value) { return new Attempt<>(value, null); }
        static <T> Attempt<T> error(String error) { return new Attempt<>(null, error); }
    }
    private static <T> T resolve(Attempt<T> result) { if (result.error() != null) throw new ProtocolException(result.error()); return result.value(); }
}
