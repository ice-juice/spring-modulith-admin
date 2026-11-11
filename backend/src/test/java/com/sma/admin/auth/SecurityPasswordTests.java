package com.sma.admin.auth;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
class SecurityPasswordTests {
    private static final String LOGIN="m7K2v9R4";
    @Autowired UserService users;
    @Autowired SessionIssuer issuer;
    @Autowired SessionService sessions;
    @Autowired SessionManager manager;
    @Autowired SecurityPasswordService passwords;
    @Autowired SecurityPasswordVerification verification;
    @Autowired SecurityPasswordMapper rows;
    @Autowired SecurityPasswordHash hash;
    @Autowired SecuritySettingsService settings;
    @Autowired TotpEnrollmentService enrollment;
    @Autowired TotpEnrollmentConfirmation confirmation;
    @Autowired MfaLoginService mfa;
    @Autowired MockMvcTester mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.sma.admin.iam.internal.persistence.AccountMapper fixtureAccounts;
    @MockitoSpyBean SecurityChallenges challengeClock;
    private Instant testTime;
    @BeforeEach void freezeOtpTime(){testTime=Instant.now();org.mockito.Mockito.doReturn(testTime).when(challengeClock).now();}
    private record Fixture(AccountView user,String token,long sessionId,SmaPrincipal principal){}
    private Fixture fixture(){String name="sp"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        users.create(new UserService.Create(new UserService.Input(name,name,null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),"en-US","UTC"),LOGIN));
        return login(users.authenticate(name,LOGIN,"admin").orElseThrow());}
    private Fixture login(AccountView user){var result=issuer.issue(user,false,Duration.ofHours(8),"test","test");return fromToken(user,result.token());}
    private Fixture fromToken(AccountView user,String token){var session=sessions.resolve(token).orElseThrow();return new Fixture(user,token,session.session().getId(),session.principal());}
    private <T>T as(Fixture f,java.util.function.Supplier<T> action){return Users.as(f.principal(),action);}
    private Fixture setup(Fixture f,String candidate){as(f,()->{passwords.set(f.sessionId(),LOGIN,null,candidate,candidate,"setup-"+f.user().id());return null;});return login(f.user());}
    private String code(String secret,int offset)throws Exception{return new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30),6).generateOneTimePasswordString(new SecretKeySpec(new Base32().decode(secret),"HmacSHA1"),testTime.plusSeconds(offset*30L),Locale.ROOT);}
    @Test void allThreeLengthsWorkAndOnlyHashesAreStoredWhileChangesInvalidateSessions(){
        for(String candidate:List.of("a1b2c3","a1b2c3d","n8L3w0S5")){
            var original=fixture();var active=setup(original,candidate);assertThat(sessions.resolve(original.token())).isEmpty();
            var row=rows.selectOne(new LambdaQueryWrapper<SecurityPasswordEntity>().eq(SecurityPasswordEntity::getUserId,active.user().id()));
            assertThat(row.getPasswordHash()).startsWith("$argon2id$").doesNotContain(candidate);assertThat(row.getPepperKeyId()).isNotBlank();
            as(active,()->{assertThat(settings.get(active.sessionId()).securityPasswordSet()).isTrue();verification.verifyForCurrentSession(active.sessionId(),candidate);
                passwords.change(active.sessionId(),LOGIN,null,candidate,"v4N8p2","v4N8p2","change-"+active.user().id());return null;});
            assertThat(sessions.resolve(active.token())).isEmpty();var again=login(active.user());
            as(again,()->{assertThatThrownBy(()->verification.verifyForCurrentSession(again.sessionId(),candidate)).isInstanceOf(BizException.class);verification.verifyForCurrentSession(again.sessionId(),"v4N8p2");return null;});
        }
    }
    @Test void unsetLockedAndWrongPasswordsFailClosedAndFailureBudgetSurvivesRollback(){
        var f=fixture();as(f,()->{assertThatThrownBy(()->verification.verifyForCurrentSession(f.sessionId(),"a1b2c3")).isInstanceOf(BizException.class);return null;});
        var active=setup(f,"n8L3w0S5");
        as(active,()->{manager.lock(active.sessionId());assertThatThrownBy(()->verification.verifyForCurrentSession(active.sessionId(),"n8L3w0S5")).isInstanceOf(BizException.class);manager.unlock(active.sessionId(),LOGIN,"unlock-"+f.user().id());return null;});
        as(active,()->{for(int i=0;i<4;i++)assertThatThrownBy(()->verification.verifyForCurrentSession(active.sessionId(),"bad999")).isInstanceOf(BizException.class);
            assertThatThrownBy(()->verification.verifyForCurrentSession(active.sessionId(),"n8L3w0S5")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.RATE_LIMITED));return null;});
    }
    @Test void formatAndEqualityAreCheckedBeforeWritesAndUnboundResetIsNotAllowed(){
        var f=fixture();as(f,()->{
            for(String candidate:List.of("123456","abcdef","a1b2c","a1b2c3d4e","a1b2c!"))assertThatThrownBy(()->passwords.set(f.sessionId(),LOGIN,null,candidate,candidate,"format-"+f.user().id())).isInstanceOf(BizException.class);
            assertThatThrownBy(()->passwords.set(f.sessionId(),LOGIN,null,LOGIN,LOGIN,"format-"+f.user().id())).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IamErrorCode.PASSWORD_CONFLICT));return null;});
        assertThat(rows.selectCount(new LambdaQueryWrapper<SecurityPasswordEntity>().eq(SecurityPasswordEntity::getUserId,f.user().id()))).isZero();
        var active=setup(f,"n8L3w0S5");
        as(active,()->{assertThatThrownBy(()->passwords.reset(active.sessionId(),LOGIN,null,"v4N8p2","v4N8p2","reset-"+f.user().id())).isInstanceOf(BizException.class);verification.verifyForCurrentSession(active.sessionId(),"n8L3w0S5");return null;});
    }
    @Test void twoPasswordsCannotConcurrentlyBecomeTheSameValue()throws Exception{
        var active=setup(fixture(),"n8L3w0S5");String candidate="t6R9k3M2";
        try(var executor=Executors.newFixedThreadPool(2)){
            Callable<Boolean> loginChange=()->as(active,()->{try{users.changeOwnPassword(LOGIN,candidate);return true;}catch(BizException rejected){return false;}});
            Callable<Boolean> securityChange=()->as(active,()->{try{passwords.change(active.sessionId(),LOGIN,null,"n8L3w0S5",candidate,candidate,"race-"+active.user().id());return true;}catch(BizException rejected){return false;}});
            int successes=0;for(var task:executor.invokeAll(List.of(loginChange,securityChange)))if(task.get(30,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);
        }
        var row=rows.selectOne(new LambdaQueryWrapper<SecurityPasswordEntity>().eq(SecurityPasswordEntity::getUserId,active.user().id()));
        boolean loginMatches=users.matchesPassword(active.user().id(),candidate),securityMatches=hash.matches(candidate,row.getPasswordHash(),row.getPepperKeyId());
        assertThat(loginMatches&&securityMatches).isFalse();assertThat(loginMatches||securityMatches).isTrue();
    }
    @Test void resetRequiresBoundTotpAndRotatesSessions()throws Exception{
        var active=setup(fixture(),"n8L3w0S5");var pending=as(active,()->enrollment.begin(active.sessionId(),LOGIN,null,"reset-bind-"+active.user().id()));
        String initial=code(pending.secret(),-1);as(active,()->{confirmation.confirm(active.sessionId(),pending.challengeToken(),initial,"reset-bind-"+active.user().id());return null;});
        var challenge=issuer.begin(active.user(),false,Duration.ofHours(8),"test","test",false);
        var authenticated=fromToken(active.user(),mfa.verify(challenge.challengeToken(),code(pending.secret(),0),"reset-login-"+active.user().id(),"test").token());
        String proof=code(pending.secret(),1);
        as(authenticated,()->{assertThatThrownBy(()->passwords.reset(authenticated.sessionId(),LOGIN,null,"v4N8p2","v4N8p2","reset-"+active.user().id())).isInstanceOf(BizException.class);
            passwords.reset(authenticated.sessionId(),LOGIN,proof,"v4N8p2","v4N8p2","reset-"+active.user().id());return null;});
        assertThat(sessions.resolve(authenticated.token())).isEmpty();
        var row=rows.selectOne(new LambdaQueryWrapper<SecurityPasswordEntity>().eq(SecurityPasswordEntity::getUserId,active.user().id()));assertThat(hash.matches("v4N8p2",row.getPasswordHash(),row.getPepperKeyId())).isTrue();
    }
    @Test void httpEndpointDoesNotAllowAnonymousWrites(){
        assertThat(mvc.post().uri("/api/auth/security-passwords").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"m7K2v9R4\",\"newPassword\":\"n8L3w0S5\",\"confirmation\":\"n8L3w0S5\"}")).hasStatus(401);
        var f=fixture();assertThat(mvc.post().uri("/api/auth/security-passwords").header("Authorization","Bearer "+f.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"m7K2v9R4\",\"newPassword\":\"n8L3w0S5\",\"confirmation\":\"n8L3w0S5\"}")).hasStatusOk().bodyJson().hasPathSatisfying("$.code",v->v.assertThat().isEqualTo(0));
        assertThat(sessions.resolve(f.token())).isEmpty();
    }
    @Test void verificationCannotUseAnEarlierBusinessTransactionSnapshotAfterCredentialRevocation()throws Exception{
        var active=setup(fixture(),"n8L3w0S5");
        try(var executor=Executors.newSingleThreadExecutor()){
            as(active,()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                long oldVersion=users.findById(active.user().id()).orElseThrow().credentialVersion(); // Establish a repeatable-read snapshot.
                // Advance the authority version without deleting sessions, modelling delayed asynchronous cleanup.
                try{executor.submit(()->{var row=fixtureAccounts.selectById(active.user().id());row.setCredentialVersion(oldVersion+1);fixtureAccounts.updateById(row);}).get(20,TimeUnit.SECONDS);}catch(Exception failure){throw new AssertionError(failure);}
                assertThat(users.findById(active.user().id()).orElseThrow().credentialVersion()).isEqualTo(oldVersion);
                assertThatThrownBy(()->verification.verifyForCurrentSession(active.sessionId(),"n8L3w0S5")).isInstanceOf(BizException.class);
                status.setRollbackOnly();return null;
            }));
        }
    }
}
