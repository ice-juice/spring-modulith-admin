package com.sma.release;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.sma.extension.protocol.*;
import com.sma.release.auth.*;
import com.sma.release.auth.SecurityRecords.*;
import com.sma.release.crypto.SecretVault;
import com.sma.release.store.ReleaseDatabase;

class IdentityServiceTests {
    private static final String LOGIN = "LocalTestLogin!247";
    private static final String OPERATION = "LocalTestOperation!913";
    private final MutableClock clock = new MutableClock();
    private ReleaseDatabase database;
    private IdentityService identity;
    private String secret;
    private String session;
    private NewAccount account;
    private List<String> recoveryCodes;
    @BeforeEach void setup() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        database = new ReleaseDatabase(new JdbcTemplate(dataSource), new TransactionTemplate(new DataSourceTransactionManager(dataSource)), clock);
        var properties = new ReleaseProperties("security", "host", Path.of("target/test-vault"), java.util.Base64.getEncoder().encodeToString(new byte[32]), "https://release.example.test", "");
        identity = new IdentityService(database, new CredentialService(new SecretVault(properties)));
        account = identity.bootstrap("operator", LOGIN, OPERATION);
        var enrollment = identity.beginEnrollment("operator", LOGIN, account.enrollmentToken(), "127.0.0.1"); secret = enrollment.secret();
        recoveryCodes=identity.completeEnrollment("operator", LOGIN, account.enrollmentToken(), otp(), "127.0.0.1");
        assertThat(recoveryCodes).hasSize(10);
        clock.advance(30); session = identity.login("operator", LOGIN, otp(), "127.0.0.1").token();
    }
    @Test void loginTotpCannotBeReusedForAnOperationAndFailuresArePersisted() throws Exception {
        var challenge = identity.challenge(session, intent("a"));
        assertThatThrownBy(() -> identity.authorize(session, challenge.id(), OPERATION, otp())).hasMessage("authentication-failed");
        int failures = database.transaction(tx -> tx.get("throttle", "operation:" + account.userId(), Throttle.class).orElseThrow().failures());
        assertThat(failures).isEqualTo(1);
        clock.advance(30);
        var grant = identity.authorize(session, challenge.id(), OPERATION, otp());
        assertThat(grant.token()).isNotBlank();
        assertThatThrownBy(() -> identity.authorize(session, challenge.id(), OPERATION, otp())).hasMessage("challenge-unavailable");
    }
    @Test void exactIntentAndDatabaseAtomicConsumptionPreventConcurrentReplay() throws Exception {
        var expected = intent("a"); var challenge = identity.challenge(session, expected); clock.advance(30);
        var grant = identity.authorize(session, challenge.id(), OPERATION, otp());
        assertThatThrownBy(() -> database.transaction(tx -> identity.consume(tx, session, grant.token(), intent("b"), "wrong"))).hasMessage("grant-unavailable");
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i=0; i<8; i++) attempts.add(() -> {
            try { database.transaction(tx -> identity.consume(tx, session, grant.token(), expected, UUID.randomUUID().toString())); return true; }
            catch (ProtocolException denied) { return false; }
        });
        try (var executor = Executors.newFixedThreadPool(8)) {
            var outcomes = executor.invokeAll(attempts);
            int accepted = 0; for (var outcome : outcomes) if (outcome.get()) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
    }
    @Test void expiredGrantAndLogoutInvalidateAuthorization() throws Exception {
        var expected = intent("a"); var challenge = identity.challenge(session, expected); clock.advance(30);
        var grant = identity.authorize(session, challenge.id(), OPERATION, otp()); clock.advance(60);
        assertThatThrownBy(() -> database.transaction(tx -> identity.consume(tx, session, grant.token(), expected, "expired"))).hasMessage("grant-unavailable");
        identity.logout(session);
        assertThatThrownBy(() -> identity.challenge(session, expected)).hasMessage("unauthenticated");
    }
    @Test void bootstrapAndEnrollmentCannotBeReopenedAndSecretsAreEncrypted() {
        assertThatThrownBy(() -> identity.bootstrap("another", LOGIN, OPERATION)).hasMessage("bootstrap-already-completed");
        assertThatThrownBy(() -> identity.beginEnrollment("operator", LOGIN, account.enrollmentToken(), "127.0.0.1")).hasMessage("authentication-failed");
        var user = database.transaction(tx -> tx.get("user", account.userId(), User.class).orElseThrow());
        assertThat(user.loginHash()).startsWith("$argon2id$").doesNotContain(LOGIN);
        assertThat(user.operationHash()).startsWith("$argon2id$").doesNotContain(OPERATION);
        assertThat(user.sealedTotp()).doesNotContain(secret);
        assertThat(user.enrollmentTokenHash()).isNull();
    }
    @Test void factorRecoveryImmediatelyInvalidatesSessionsAndCannotReuseTheRecoveryCode() {
        var ticket=identity.recoverFactor("operator",LOGIN,recoveryCodes.getFirst(),"127.0.0.1");
        assertThat(ticket.userId()).isEqualTo(account.userId());
        assertThatThrownBy(() -> identity.principal(session)).hasMessage("unauthenticated");
        assertThatThrownBy(() -> identity.recoverFactor("operator",LOGIN,recoveryCodes.getFirst(),"127.0.0.1")).hasMessage("authentication-failed");
        var enrollment=identity.beginEnrollment("operator",LOGIN,ticket.enrollmentToken(),"127.0.0.1");
        assertThat(enrollment.secret()).isNotEqualTo(secret);
    }
    private ActionIntent intent(String marker) {
        return new ActionIntent(ActionIntent.Action.UPLOAD, "production", "general", 1, "demo", 0, 1,
                List.of(new ActionIntent.FileIntent("demo.jar", marker.repeat(64), 20)), Hashes.sha256("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private String otp() throws Exception {
        var generator = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
        return String.format(java.util.Locale.ROOT, "%06d", generator.generateOneTimePassword(new SecretKeySpec(new Base32().decode(secret), "HmacSHA1"), clock.instant()));
    }
    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-10T00:00:00Z");
        void advance(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
