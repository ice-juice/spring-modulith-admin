package com.sma.admin.auth;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.auth.internal.service.SecurityRateLimit;
import com.sma.admin.auth.internal.service.SecurityChallenges;
import com.sma.admin.auth.internal.persistence.SecurityChallengeEntity;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class AuthSecurityPersistenceTests {
    @Autowired SecurityRateLimit limits;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SecurityChallenges challenges;
    @Autowired com.sma.admin.auth.internal.persistence.TotpCredentialMapper totps;
    @Autowired com.sma.admin.auth.internal.persistence.SecurityChallengeMapper challengeMapper;
    @Autowired com.sma.admin.auth.internal.persistence.SecurityThrottleMapper throttleMapper;
    @Autowired com.sma.admin.auth.internal.persistence.SecurityPasswordMapper passwordMapper;
    @Autowired com.sma.admin.auth.internal.persistence.SessionGuardMapper guardMapper;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test void encryptionRotationIsTenantScopedRestartableAndPreservesReplayState() throws Exception {
        byte[] key1=new byte[32],key2=new byte[32],pepper=new byte[32];
        Arrays.fill(key1,(byte)11);Arrays.fill(key2,(byte)22);Arrays.fill(pepper,(byte)33);
        String ring="\nencryption.Key_A="+Base64.getEncoder().encodeToString(key1)+"\nencryption.key_a="+Base64.getEncoder().encodeToString(key2)
                +"\npepper.active=p\npepper.p="+Base64.getEncoder().encodeToString(pepper);
        var oldFile=directory.resolve("old.keys");var newFile=directory.resolve("new.keys");
        java.nio.file.Files.writeString(oldFile,"encryption.active=Key_A"+ring);
        java.nio.file.Files.writeString(newFile,"encryption.active=key_a"+ring);
        var old=new com.sma.admin.auth.internal.service.SecuritySecrets(new com.sma.admin.auth.internal.service.AccountSecurityProperties(oldFile.toString(),"SMA"));
        var current=new com.sma.admin.auth.internal.service.SecuritySecrets(new com.sma.admin.auth.internal.service.AccountSecurityProperties(newFile.toString(),"SMA"));
        var maintenance=new com.sma.admin.auth.internal.service.SecurityMaintenance(challengeMapper,throttleMapper,totps,passwordMapper,guardMapper,current);
        var tx=new TransactionTemplate(transactions);
        long id=TenantScope.within(919L,()->tx.execute(status->{
            var row=new com.sma.admin.auth.internal.persistence.TotpCredentialEntity();row.setUserId(42L);row.setPortal("admin");row.setEnabled(true);
            row.setEnabledAt(java.time.Instant.now());row.setLastAcceptedStep(12345L);
            row.setSecretCiphertext(old.encrypt("active-secret","919:42:admin:totp"));
            row.setPendingSecretCiphertext(old.encrypt("pending-secret","919:42:admin:totp:pending"));
            row.setPendingChallengeId(92L);row.setPendingExpiresAt(java.time.Instant.now().plusSeconds(600));totps.insert(row);return row.getId();
        }));
        TenantScope.within(920L,()->tx.execute(status->{assertThat(maintenance.rotateEncryption()).isZero();return null;}));
        TenantScope.within(919L,()->tx.execute(status->{
            assertThat(maintenance.rotateEncryption()).isEqualTo(1);
            var row=totps.selectById(id);assertThat(row.getSecretCiphertext()).startsWith("v1.key_a.");
            assertThat(current.decrypt(row.getSecretCiphertext(),"919:42:admin:totp")).isEqualTo("active-secret");
            assertThat(current.decrypt(row.getPendingSecretCiphertext(),"919:42:admin:totp:pending")).isEqualTo("pending-secret");
            assertThat(row.getLastAcceptedStep()).isEqualTo(12345L);assertThat(row.getEnabled()).isTrue();assertThat(row.getPendingChallengeId()).isEqualTo(92L);
            assertThat(maintenance.rotateEncryption()).isZero();return null;
        }));
    }
    @Test void onlyFiveConcurrentAttemptsCanReserveBudget() throws Exception {
        String subject=UUID.randomUUID().toString();
        try(var executor=Executors.newFixedThreadPool(12)) {
            var tasks=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<12;i++) tasks.add(()->TenantScope.within(810L,()->{
                try {limits.claim("TEST",subject,5,Duration.ofMinutes(15));return true;}
                catch(BizException rejected){assertThat(rejected.getErrorCode()).isEqualTo(AuthErrorCode.RATE_LIMITED);return false;}
            }));
            int accepted=0;for(var result:executor.invokeAll(tasks))if(result.get(20,TimeUnit.SECONDS))accepted++;
            assertThat(accepted).isEqualTo(5);
        }
        TenantScope.within(811L,()->{limits.claim("TEST",subject,5,Duration.ofMinutes(15));return null;});
    }
    @Test void outerTransactionRollbackCannotRefundAnAttempt() {
        String subject=UUID.randomUUID().toString();
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{limits.claim("ROLLBACK",subject,1,Duration.ofMinutes(15));status.setRollbackOnly();});
        assertThatThrownBy(()->limits.claim("ROLLBACK",subject,1,Duration.ofMinutes(15))).isInstanceOf(BizException.class);
    }
    @Test void challengesArePurposeAndTenantBoundAndOneUseWithPersistentAttempts() {
        var tx=new TransactionTemplate(transactions);
        String token=tx.execute(status->{
            var row=new SecurityChallengeEntity();row.setUserId(42L);row.setPortal("admin");row.setPurpose("LOGIN_TOTP");
            row.setSecurityVersion(0L);row.setAccountCredentialVersion(0L);row.setExpiresAt(challenges.now().plusSeconds(180));
            row.setSingleTerminal(false);row.setSessionTtlSeconds(28800L);return challenges.insert(row);
        });
        assertThatThrownBy(()->challenges.reserve(token,"TOTP_ENROLL")).isInstanceOf(BizException.class);
        TenantScope.within(812L,()->{assertThatThrownBy(()->challenges.reserve(token,"LOGIN_TOTP")).isInstanceOf(BizException.class);return null;});
        tx.executeWithoutResult(status->{challenges.reserve(token,"LOGIN_TOTP");status.setRollbackOnly();});
        assertThat(challenges.reserve(token,"LOGIN_TOTP").getAttemptCount()).isEqualTo(2);
        tx.executeWithoutResult(status->challenges.consume(challenges.lock(token,"LOGIN_TOTP")));
        assertThatThrownBy(()->challenges.reserve(token,"LOGIN_TOTP")).isInstanceOf(BizException.class);
    }
}
