package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sma.admin.identity.EmailIdentityTestSupport.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.identity.internal.service.IdentityEmailChallengeStore.*;
import com.sma.admin.mail.internal.persistence.MailDeliveryMapper;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.IntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.*;

@IntegrationTest
class IdentityPasswordLoginTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] value=new byte[32];new SecureRandom().nextBytes(value);keyFile=Files.createTempFile("sma-password-login-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(value));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityEmailConfigurationService configurations;
    @Autowired MailProfileService profiles;@Autowired MailTemplateService templates;@Autowired IdentityEmailAccountService accounts;
    @Autowired IdentityEmailChallengeStore challenges;@Autowired IdentityEmailChallengeMapper challengeRows;@Autowired MailDeliveryMapper mailRows;@Autowired MailOutboxService outbox;
    @Autowired IdentityEmailCredentialMapper credentials;@Autowired IdentitySubjectMapper subjects;@Autowired IdentitySessionMapper sessionRows;@Autowired IdentityEmailBudgetMapper budgets;
    @Autowired IdentitySessionStore sessions;@Autowired IdentityPasswordLoginStore loginStore;@Autowired IdentityPasswordLoginCoordinator login;@Autowired TransactionTemplate tx;@Autowired PasswordEncoder encoder;
    @MockitoSpyBean IdentityClock clock;@MockitoSpyBean MailWorkClock mailClock;@MockitoSpyBean IdentityPasswordWork passwords;
    private Instant now;private final String password="Q7d4e10594",replacement="m7K2v9R4";
    @BeforeEach void time(){
        now=Instant.parse("2026-11-12T00:00:00.123456789Z");doAnswer(i->now).when(clock).now();doAnswer(i->now).when(mailClock).now();
        doAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return i.callRealMethod();}).when(passwords).matches(anyString(),nullable(String.class));
    }
    private Fixture fixture(long tenant){return EmailIdentityTestSupport.fixture(tenant,realms,clients,configurations,profiles,templates);}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private Use grant(Fixture f,String address,Purpose purpose){
        var issued=job(f.tenant(),()->accounts.start(new IdentityEmailAccountService.Challenge(f.client().loginHandle(),"en-US",purpose,address,ORIGIN,"127.0.0.1")));
        var row=job(f.tenant(),()->challengeRows.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().eq(IdentityEmailChallengeEntity::getHandleHash,hash(issued.handle()))));
        String body=job(f.tenant(),()->outbox.payload(mailRows.selectById(row.getDeliveryId())).body());var matcher=Pattern.compile("Code ([0-9]{6}),").matcher(body);assertThat(matcher.find()).isTrue();
        var result=job(f.tenant(),()->challenges.verify(new Verification(issued.handle(),issued.proof(),matcher.group(1),f.client().loginHandle(),purpose,ORIGIN,"127.0.0.1")));
        assertThat(result.error()).isNull();return new Use(result.grant(),issued.proof(),f.client().loginHandle(),purpose,ORIGIN);
    }
    private IdentitySessionStore.Issued register(Fixture f,String email){var grant=grant(f,email,Purpose.REGISTRATION);return job(f.tenant(),()->accounts.register(grant,password));}
    private IdentitySessionStore.Issued signIn(Fixture f,String email,String password,String source){return job(f.tenant(),()->login.login(new IdentityPasswordLoginCoordinator.Login(f.client().loginHandle(),email,password,ORIGIN,source)));}
    private IdentityPasswordLoginStore.Attempt attempt(Fixture f,String email,String source){return new IdentityPasswordLoginStore.Attempt(f.client().loginHandle(),email,ORIGIN,source);}
    private IdentityEmailCredentialEntity credential(Fixture f){return job(f.tenant(),()->credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>()));}
    @Test void passwordProofRunsOutsideTransactionAndIssuesAClientBoundPasswordSession(){
        var f=fixture(1201);var registration=register(f,"login@example.test");now=now.plusSeconds(10);
        var signed=signIn(f,"login@example.test",password,"10.0.0.1");assertThat(signed.subjectId()).isEqualTo(registration.subjectId());assertThat(signed.sessionId()).isNotEqualTo(registration.sessionId());
        var access=job(f.tenant(),()->sessions.authenticate(signed.accessToken(),f.client().loginHandle(),"WEB"));assertThat(access.authenticationMethod()).isEqualTo("PASSWORD");
        assertThat(access.authenticatedAt()).isEqualTo(now.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThatThrownBy(()->job(f.tenant(),()->sessions.authenticate(signed.accessToken(),f.client().loginHandle(),"NATIVE"))).isInstanceOf(BizException.class);
        verify(passwords).matches(password,credential(f).getPasswordHash());
    }
    @Test void wrongUnknownAndDisabledAccountsHaveSameFailureAndUnknownCasesStillHash(){
        var f=fixture(1202);var registered=register(f,"known@example.test");
        assertThatThrownBy(()->signIn(f,"known@example.test","wrongpass1","10.0.0.1")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_PASSWORD_LOGIN));
        assertThatThrownBy(()->signIn(f,"unknown@example.test","wrongpass1","10.0.0.1")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_PASSWORD_LOGIN));
        job(f.tenant(),()->{var subject=subjects.selectById(registered.subjectId());subject.setEnabled(false);subjects.updateById(subject);return null;});
        assertThatThrownBy(()->signIn(f,"known@example.test","wrongpass1","10.0.0.1")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_PASSWORD_LOGIN));
        verify(passwords,times(2)).matches("wrongpass1",null);assertThat(job(f.tenant(),()->sessionRows.selectCount(null))).isEqualTo(1);
    }
    @Test void targetGuessBudgetSurvivesOuterRollbackAndCannotBeBypassedWithAnotherSource(){
        var f=fixture(1203);register(f,"limited@example.test");int before=job(f.tenant(),()->budgets.selectOne(new LambdaQueryWrapper<IdentityEmailBudgetEntity>()).getMinuteGuesses());
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->{loginStore.prepare(attempt(f,"limited@example.test","10.0.0.1"));throw new IllegalStateException("outer rollback");})))
                .isInstanceOf(IllegalStateException.class);
        for(int i=2;i<=5;i++){String source="10.0.0."+i;assertThatThrownBy(()->signIn(f,"limited@example.test","wrongpass1",source)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_PASSWORD_LOGIN));}
        assertThatThrownBy(()->signIn(f,"limited@example.test",password,"10.0.0.9")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.EMAIL_CHALLENGE_LIMIT));
        int after=job(f.tenant(),()->budgets.selectOne(new LambdaQueryWrapper<IdentityEmailBudgetEntity>()).getMinuteGuesses());assertThat(after).isEqualTo(before+6);
        now=now.plusSeconds(61);assertThat(signIn(f,"limited@example.test",password,"10.0.0.9").subjectId()).isPositive();
    }
    @Test void passwordChangedDuringHashVerificationMakesThePreparedProofUnusable(){
        var f=fixture(1204);register(f,"changed@example.test");now=now.plusSeconds(61);
        var prepared=job(f.tenant(),()->loginStore.prepare(attempt(f,"changed@example.test","10.0.0.1")));assertThat(passwords.matches(password,prepared.passwordHash())).isTrue();
        var recovery=grant(f,"changed@example.test",Purpose.PASSWORD_RESET);job(f.tenant(),()->{accounts.resetPassword(recovery,replacement);return null;});
        assertThatThrownBy(()->job(f.tenant(),()->loginStore.finish(prepared))).isInstanceOf(BizException.class);
        assertThat(signIn(f,"changed@example.test",replacement,"10.0.0.1").subjectId()).isEqualTo(prepared.subjectId());
        assertThatThrownBy(()->signIn(f,"changed@example.test",password,"10.0.0.1")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_PASSWORD_LOGIN));
    }
    @Test void stalePreparationAndConfigurationDisableCannotIssueSessions(){
        var f=fixture(1205);register(f,"deadline@example.test");var prepared=job(f.tenant(),()->loginStore.prepare(attempt(f,"deadline@example.test","10.0.0.1")));
        now=now.plusSeconds(31);assertThatThrownBy(()->job(f.tenant(),()->loginStore.finish(prepared))).isInstanceOf(BizException.class);
        var current=job(f.tenant(),()->loginStore.prepare(attempt(f,"deadline@example.test","10.0.0.1")));admin(f.tenant(),()->clients.disable(f.client().id(),f.client().version()));
        assertThatThrownBy(()->job(f.tenant(),()->loginStore.finish(current))).isInstanceOf(BizException.class);assertThat(job(f.tenant(),()->sessionRows.selectCount(null))).isEqualTo(1);
    }
    @Test void legacySymbolPasswordRemainsVerifiableWithoutApplyingNewWritePolicy(){
        var f=fixture(1206);register(f,"legacy@example.test");String legacy="Historical!Password2025";
        job(f.tenant(),()->{var row=credential(f);row.setPasswordHash(encoder.encode(legacy));credentials.updateById(row);return null;});
        assertThat(signIn(f,"legacy@example.test",legacy,"10.0.0.1").subjectId()).isPositive();
    }
    @Test void trustedTenantRealmOriginAndWorkerBoundariesRemainMandatory(){
        var f=fixture(1207);register(f,"scope@example.test");var other=fixture(1208);
        assertThatThrownBy(()->signIn(other,"scope@example.test",password,"10.0.0.1")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_PASSWORD_LOGIN));
        assertThatThrownBy(()->job(f.tenant(),()->login.login(new IdentityPasswordLoginCoordinator.Login(f.client().loginHandle(),"scope@example.test",password,"https://evil.example.test","10.0.0.1"))))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(()->admin(f.tenant(),()->login.login(new IdentityPasswordLoginCoordinator.Login(f.client().loginHandle(),"scope@example.test",password,ORIGIN,"10.0.0.1"))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->login.login(new IdentityPasswordLoginCoordinator.Login(f.client().loginHandle(),"scope@example.test",password,ORIGIN,"10.0.0.1")))))
                .isInstanceOf(IllegalStateException.class);
        var shared=admin(f.tenant(),()->clients.create(new IdentityClientService.Input(f.realm().id(),"other","Other","other",IdentityClientService.Platform.WEB,true,false,null,null,Set.of(ORIGIN+"/cb"),Set.of(ORIGIN),900,2592000)));
        var issued=job(f.tenant(),()->login.login(new IdentityPasswordLoginCoordinator.Login(shared.loginHandle(),"scope@example.test",password,ORIGIN,"10.0.0.1")));
        assertThat(issued.application()).isEqualTo("other");assertThatThrownBy(()->job(f.tenant(),()->sessions.authenticate(issued.accessToken(),f.client().loginHandle(),"WEB"))).isInstanceOf(BizException.class);
    }
}
