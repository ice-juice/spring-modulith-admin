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
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.identity.internal.service.IdentityEmailChallengeStore.*;
import com.sma.admin.mail.internal.persistence.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.IntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class IdentityEmailAccountTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] value=new byte[32];new SecureRandom().nextBytes(value);keyFile=Files.createTempFile("sma-email-accounts-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(value));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityEmailConfigurationService configurations;
    @Autowired MailProfileService profiles;@Autowired MailTemplateService templates;@Autowired IdentityEmailAccountService accounts;
    @Autowired IdentityEmailChallengeStore challenges;@Autowired IdentityEmailChallengeMapper challengeRows;@Autowired MailDeliveryMapper mailRows;@Autowired MailOutboxService outbox;
    @Autowired IdentityEmailCredentialMapper credentials;@Autowired IdentitySubjectMapper subjects;@Autowired IdentitySessionMapper sessionRows;@Autowired IdentityExternalMapper externals;
    @Autowired IdentitySessionStore sessions;@Autowired IdentityAuthority authority;@Autowired PasswordEncoder encoder;@Autowired TransactionTemplate tx;
    @MockitoSpyBean IdentityClock clock;@MockitoSpyBean MailWorkClock mailClock;@MockitoSpyBean TenantSecretCodec secrets;
    private Instant now;private final String password="Q7d4e10594",replacement="m7K2v9R4";
    @BeforeEach void time(){now=Instant.parse("2026-11-11T00:00:00.123456789Z");doAnswer(i->now).when(clock).now();doAnswer(i->now).when(mailClock).now();}
    private Fixture fixture(long tenant){return EmailIdentityTestSupport.fixture(tenant,realms,clients,configurations,profiles,templates);}
    private IdentityEmailCredentialEntity credential(long tenant){return job(tenant,()->credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>()));}
    private IdentityEmailChallengeEntity challenge(long tenant,String handle){return job(tenant,()->challengeRows.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().eq(IdentityEmailChallengeEntity::getHandleHash,hash(handle))));}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private record Grant(IdentityEmailDeliveryService.Issued issued,Use use){}
    private Grant grant(Fixture f,String address,Purpose purpose){
        var issued=job(f.tenant(),()->accounts.start(new IdentityEmailAccountService.Challenge(f.client().loginHandle(),"en-US",purpose,address,ORIGIN,"127.0.0.1")));
        var row=challenge(f.tenant(),issued.handle());String body=job(f.tenant(),()->outbox.payload(mailRows.selectById(row.getDeliveryId())).body());
        var matcher=Pattern.compile("Code ([0-9]{6}),").matcher(body);assertThat(matcher.find()).isTrue();String code=matcher.group(1);
        var verified=job(f.tenant(),()->challenges.verify(new Verification(issued.handle(),issued.proof(),code,f.client().loginHandle(),purpose,ORIGIN,"127.0.0.1")));
        assertThat(verified.error()).isNull();return new Grant(issued,new Use(verified.grant(),issued.proof(),f.client().loginHandle(),purpose,ORIGIN));
    }
    private IdentitySessionStore.Issued register(Fixture f,String address){var grant=grant(f,address,Purpose.REGISTRATION);return job(f.tenant(),()->accounts.register(grant.use(),password));}
    private IdentitySessionStore.Issued google(Fixture f,String email){return job(f.tenant(),()->sessions.google(authority.capture(f.client().loginHandle()),new GoogleIdTokenVerifier.Verified("https://accounts.google.com","google-sub",email,true)));}
    @Test void registrationStoresOnlyHashAndEncryptedEmailAndRefreshDoesNotManufactureNewAuthentication(){
        var f=fixture(1181);var issued=register(f,"Owner@EXAMPLE.TEST");var credential=credential(f.tenant());
        assertThat(credential.getSubjectId()).isEqualTo(issued.subjectId());assertThat(credential.getPasswordHash()).startsWith("$2").isNotEqualTo(password);assertThat(encoder.matches(password,credential.getPasswordHash())).isTrue();
        assertThat(credential.getEmailEnvelope()).startsWith("v1.test.").doesNotContain("Owner@example.test");
        job(f.tenant(),()->{try(var value=secrets.decrypt(new TenantConfigurationRef(f.tenant(),"identity",credential.getId(),1,1),"verified-email-address",credential.getEmailEnvelope())){
            assertThat(value.copy()).containsExactly("Owner@example.test".toCharArray());}return null;});
        var original=job(f.tenant(),()->sessions.authenticate(issued.accessToken(),f.client().loginHandle(),"WEB"));assertThat(original.authenticationMethod()).isEqualTo("EMAIL_VERIFIED");
        now=now.plusSeconds(30);var refreshed=job(f.tenant(),()->sessions.refresh(issued.refreshToken(),f.client().loginHandle(),"WEB",issued.csrfToken()));
        var later=job(f.tenant(),()->sessions.authenticate(refreshed.accessToken(),f.client().loginHandle(),"WEB"));assertThat(later.authenticatedAt()).isEqualTo(original.authenticatedAt());
        assertThat(later.authenticationMethod()).isEqualTo("EMAIL_VERIFIED");
    }
    @Test void weakPasswordAndEncryptionFailureDoNotConsumeGrantOrLeaveHalfAnAccount(){
        var f=fixture(1182);var grant=grant(f,"weak@example.test",Purpose.REGISTRATION);
        assertThatThrownBy(()->job(f.tenant(),()->accounts.register(grant.use(),"password1"))).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.WEAK_PASSWORD));
        assertThat(challenge(f.tenant(),grant.issued().handle()).getStatus()).isEqualTo("VERIFIED");assertThat(credential(f.tenant())).isNull();
        doThrow(new BizException(CommonErrorCode.SERVICE_UNAVAILABLE)).when(secrets).encrypt(any(TenantConfigurationRef.class),eq("verified-email-address"),any(char[].class));
        assertThatThrownBy(()->job(f.tenant(),()->accounts.register(grant.use(),password))).isInstanceOf(BizException.class);
        assertThat(job(f.tenant(),()->subjects.selectCount(null))).isZero();assertThat(job(f.tenant(),()->sessionRows.selectCount(null))).isZero();assertThat(credential(f.tenant())).isNull();
        assertThat(challenge(f.tenant(),grant.issued().handle()).getStatus()).isEqualTo("VERIFIED");
    }
    @Test void registrationOuterRollbackPreservesGrantAndRetryCreatesExactlyOneAccount(){
        var f=fixture(1183);var grant=grant(f,"rollback@example.test",Purpose.REGISTRATION);
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->{accounts.register(grant.use(),password);throw new IllegalStateException("outer rollback");}))).isInstanceOf(IllegalStateException.class);
        assertThat(credential(f.tenant())).isNull();assertThat(job(f.tenant(),()->subjects.selectCount(null))).isZero();assertThat(job(f.tenant(),()->sessionRows.selectCount(null))).isZero();
        assertThat(challenge(f.tenant(),grant.issued().handle()).getStatus()).isEqualTo("VERIFIED");job(f.tenant(),()->accounts.register(grant.use(),password));
        assertThatThrownBy(()->job(f.tenant(),()->accounts.register(grant.use(),password))).isInstanceOf(BizException.class);assertThat(job(f.tenant(),()->subjects.selectCount(null))).isEqualTo(1);
    }
    @Test void resetMutationRevocationAndGrantConsumptionRollbackAndCommitTogether(){
        var f=fixture(1184);var original=register(f,"reset@example.test");var before=credential(f.tenant());now=now.plusSeconds(61);
        var grant=grant(f,"reset@example.test",Purpose.PASSWORD_RESET);
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->{accounts.resetPassword(grant.use(),replacement);throw new IllegalStateException("reset rollback");}))).isInstanceOf(IllegalStateException.class);
        assertThat(credential(f.tenant()).getPasswordHash()).isEqualTo(before.getPasswordHash());assertThat(challenge(f.tenant(),grant.issued().handle()).getStatus()).isEqualTo("VERIFIED");
        job(f.tenant(),()->sessions.authenticate(original.accessToken(),f.client().loginHandle(),"WEB"));
        job(f.tenant(),()->{accounts.resetPassword(grant.use(),replacement);return null;});var after=credential(f.tenant());
        assertThat(after.getPasswordVersion()).isEqualTo(2);assertThat(after.getEmailEnvelope()).isEqualTo(before.getEmailEnvelope());assertThat(encoder.matches(replacement,after.getPasswordHash())).isTrue();
        assertThatThrownBy(()->job(f.tenant(),()->sessions.authenticate(original.accessToken(),f.client().loginHandle(),"WEB"))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->job(f.tenant(),()->sessions.refresh(original.refreshToken(),f.client().loginHandle(),"WEB",original.csrfToken()))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->job(f.tenant(),()->{accounts.resetPassword(grant.use(),password);return null;})).isInstanceOf(BizException.class);
    }
    @Test void googleOnlyAndUnknownMailboxesNeverGainAPasswordThroughRecovery(){
        var f=fixture(1185);var google=google(f,"google@example.test");
        for(String address:List.of("google@example.test","unknown@example.test")){
            var grant=grant(f,address,Purpose.PASSWORD_RESET);assertThat(grant.issued().handle()).hasSize(43);assertThat(grant.issued().proof()).hasSize(43);
            assertThatThrownBy(()->job(f.tenant(),()->{accounts.resetPassword(grant.use(),replacement);return null;})).isInstanceOf(BizException.class);
        }
        assertThat(credential(f.tenant())).isNull();assertThat(job(f.tenant(),()->subjects.selectCount(null))).isEqualTo(1);
        job(f.tenant(),()->sessions.authenticate(google.accessToken(),f.client().loginHandle(),"WEB"));
    }
    @Test void sameGoogleEmailDoesNotMergeAndRepeatedRegistrationCannotReplaceCredential(){
        var f=fixture(1186);var google=google(f,"shared@example.test");var registered=register(f,"shared@example.test");assertThat(registered.subjectId()).isNotEqualTo(google.subjectId());
        assertThat(job(f.tenant(),()->subjects.selectCount(null))).isEqualTo(2);assertThat(job(f.tenant(),()->externals.selectCount(null))).isEqualTo(1);
        var original=credential(f.tenant());now=now.plusSeconds(61);var duplicate=grant(f,"shared@example.test",Purpose.REGISTRATION);
        assertThatThrownBy(()->job(f.tenant(),()->accounts.register(duplicate.use(),replacement))).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.EMAIL_ACCOUNT_CONFLICT));
        assertThat(credential(f.tenant()).getPasswordHash()).isEqualTo(original.getPasswordHash());assertThat(job(f.tenant(),()->subjects.selectCount(null))).isEqualTo(2);
    }
    @Test void resetCannotUseRegistrationGrantAndConcurrentPendingRecoveryDiesAfterCredentialChange(){
        var f=fixture(1187);var registration=grant(f,"purpose@example.test",Purpose.REGISTRATION);
        var wrong=new Use(registration.use().grant(),registration.use().proof(),f.client().loginHandle(),Purpose.PASSWORD_RESET,ORIGIN);
        assertThatThrownBy(()->job(f.tenant(),()->{accounts.resetPassword(wrong,replacement);return null;})).isInstanceOf(BizException.class);
        job(f.tenant(),()->accounts.register(registration.use(),password));now=now.plusSeconds(61);var first=grant(f,"purpose@example.test",Purpose.PASSWORD_RESET);
        now=now.plusSeconds(61);var second=grant(f,"purpose@example.test",Purpose.PASSWORD_RESET);
        job(f.tenant(),()->{accounts.resetPassword(first.use(),replacement);return null;});
        assertThatThrownBy(()->job(f.tenant(),()->{accounts.resetPassword(second.use(),password);return null;})).isInstanceOf(BizException.class);
        assertThat(encoder.matches(replacement,credential(f.tenant()).getPasswordHash())).isTrue();
    }
    @Test void anotherTenantOrClientCannotConsumeRegistrationAndAdminDoesNotBecomeIdentityCoordinator(){
        var f=fixture(1188);var foreign=fixture(1189);var grant=grant(f,"scope@example.test",Purpose.REGISTRATION);
        assertThatThrownBy(()->job(foreign.tenant(),()->accounts.register(new Use(grant.use().grant(),grant.use().proof(),foreign.client().loginHandle(),Purpose.REGISTRATION,ORIGIN),password)))
                .isInstanceOf(BizException.class);
        var other=admin(f.tenant(),()->clients.create(new IdentityClientService.Input(f.realm().id(),"other","Other","other",IdentityClientService.Platform.WEB,true,false,null,null,Set.of(ORIGIN+"/cb"),Set.of(ORIGIN),900,2592000)));
        assertThatThrownBy(()->job(f.tenant(),()->accounts.register(new Use(grant.use().grant(),grant.use().proof(),other.loginHandle(),Purpose.REGISTRATION,ORIGIN),password))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->admin(f.tenant(),()->accounts.register(grant.use(),password))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        job(f.tenant(),()->accounts.register(grant.use(),password));assertThat(credential(foreign.tenant())).isNull();
    }
    @Test void preMethodMetadataSessionsStayUsableButRefreshCannotInventFreshAuthentication(){
        var f=fixture(1190);var issued=google(f,"legacy@example.test");
        job(f.tenant(),()->{sessionRows.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IdentitySessionEntity>()
                .eq(IdentitySessionEntity::getId,issued.sessionId()).set(IdentitySessionEntity::getAuthenticationMethod,"UNKNOWN").set(IdentitySessionEntity::getAuthenticatedAt,null));return null;});
        var legacy=job(f.tenant(),()->sessions.authenticate(issued.accessToken(),f.client().loginHandle(),"WEB"));assertThat(legacy.authenticationMethod()).isEqualTo("UNKNOWN");assertThat(legacy.authenticatedAt()).isNull();
        now=now.plusSeconds(30);var next=job(f.tenant(),()->sessions.refresh(issued.refreshToken(),f.client().loginHandle(),"WEB",issued.csrfToken()));
        var refreshed=job(f.tenant(),()->sessions.authenticate(next.accessToken(),f.client().loginHandle(),"WEB"));assertThat(refreshed.authenticationMethod()).isEqualTo("UNKNOWN");assertThat(refreshed.authenticatedAt()).isNull();
    }
}
