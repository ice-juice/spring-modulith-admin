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
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.iam.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.support.*;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
class TotpAuthenticationTests {
    private static final String PASSWORD="Q7d4e10594";
    @Autowired UserService users;
    @Autowired com.sma.admin.iam.internal.persistence.UserMapper userRows;
    @Autowired SessionIssuer issuer;
    @Autowired SessionService sessions;
    @Autowired SessionManager manager;
    @Autowired SessionQuery sessionQuery;
    @Autowired TotpEnrollmentService enrollment;
    @Autowired TotpEnrollmentConfirmation confirmation;
    @Autowired MfaLoginService mfa;
    @Autowired CaptchaMapper captchas;
    @Autowired SessionMapper sessionRows;
    @Autowired TotpCredentialMapper totpRows;
    @Autowired SecurityChallengeMapper challengeRows;
    @Autowired MockMvcTester mvc;
    @Autowired JsonMapper json;
    @Autowired SecurityMaintenance maintenance;
    @MockitoSpyBean SecurityChallenges challengeClock;
    private Instant testTime;
    @BeforeEach void freezeOtpTime(){testTime=Instant.now();org.mockito.Mockito.doReturn(testTime).when(challengeClock).now();}

    private record Fixture(AccountView user,String token,long sessionId){}
    private Fixture fixture(){
        String name="mfa"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        users.create(new UserService.Create(new UserService.Input(name,name,null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),"en-US","UTC"),PASSWORD));
        var user=users.authenticate(name,PASSWORD,"admin").orElseThrow();var login=issuer.issue(user,false,Duration.ofHours(8),"127.0.0.1","test");
        return new Fixture(user,login.token(),sessions.resolve(login.token()).orElseThrow().session().getId());
    }
    private <T>T as(Fixture f,java.util.function.Supplier<T> action){return Users.as(sessions.resolve(f.token()).orElseThrow().principal(),action);}
    private String code(String secret,int offset)throws Exception{
        return new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30),6).generateOneTimePasswordString(new SecretKeySpec(new Base32().decode(secret),"HmacSHA1"),testTime.plusSeconds(offset*30L),Locale.ROOT);
    }
    private String bind(Fixture f)throws Exception{
        var pending=as(f,()->enrollment.begin(f.sessionId(),PASSWORD,null,"bind-"+f.user().id()));
        String previous=code(pending.secret(),-1);
        as(f,()->{confirmation.confirm(f.sessionId(),pending.challengeToken(),previous,"bind-"+f.user().id());return null;});
        return pending.secret();
    }
    private SessionService.LoginResult first(Fixture f){
        String id=UUID.randomUUID().toString();var captcha=new CaptchaEntity();captcha.setChallengeId(id);captcha.setAnswerHash(Tokens.hash(id+":ABCDE"));captcha.setExpiresAt(Instant.now().plusSeconds(120));captcha.setPortal("admin");captcha.setTenantSecurityGeneration(1L);captchas.insert(captcha);
        return sessions.login(f.user().username(),PASSWORD,"admin",id,"ABCDE",true,"login-"+f.user().id(),"test");
    }
    private Fixture completed(Fixture f,String token){return new Fixture(f.user(),token,sessions.resolve(token).orElseThrow().session().getId());}

    @Test void boundAccountGetsOnlyChallengeAndConcurrentExchangeIssuesOneSessionThenPasswordOnlyUnlockWorks()throws Exception{
        var f=fixture();String secret=bind(f);assertThat(sessions.resolve(f.token())).isEmpty();
        var challenge=first(f);assertThat(challenge.status()).isEqualTo("MFA_REQUIRED");assertThat(challenge.token()).isNull();assertThat(challenge.rememberAllowed()).isFalse();
        assertThat(sessionRows.selectList(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getUserId,f.user().id()))).isEmpty();
        for(String path:List.of("/api/auth/me","/api/iam/users","/api/file/files"))assertThat(mvc.get().uri(path).header("Authorization","Bearer "+challenge.challengeToken())).hasStatus(401);
        assertThat(mvc.post().uri("/api/message/socket-ticket").header("Authorization","Bearer "+challenge.challengeToken())).hasStatus(401);
        assertThat(mvc.post().uri("/api/auth/unlock").header("Authorization","Bearer "+challenge.challengeToken()).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\""+PASSWORD+"\"}")).hasStatus(401);
        assertThatThrownBy(()->issuer.issue(f.user(),false,Duration.ofHours(8),"ip","bypass")).isInstanceOf(BizException.class);
        String otp=code(secret,0);List<SessionService.LoginResult> success=new ArrayList<>();
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<SessionService.LoginResult> exchange=()->{try{return mfa.verify(challenge.challengeToken(),otp,"exchange-"+f.user().id(),"test");}catch(BizException rejected){return null;}};
            for(var future:pool.invokeAll(List.of(exchange,exchange))){var result=future.get(20,TimeUnit.SECONDS);if(result!=null)success.add(result);}
        }
        assertThat(success).hasSize(1);var logged=success.getFirst();assertThat(logged.status()).isEqualTo("AUTHENTICATED");assertThat(logged.rememberAllowed()).isFalse();
        var active=completed(f,logged.token());var before=sessionRows.selectById(active.sessionId());
        assertThat(before.getAuthenticationMethod()).isEqualTo("PASSWORD_TOTP");assertThat(before.getMfaVerifiedAt()).isNotNull();
        assertThat(Duration.between(before.getCreatedAt(),before.getExpiresAt())).isLessThanOrEqualTo(Duration.ofHours(8));
        as(active,()->{manager.lock(active.sessionId());return null;});assertThat(sessionQuery.active(active.sessionId(),f.user().id(),"admin")).isFalse();
        assertThat(mvc.post().uri("/api/auth/unlock").header("Authorization","Bearer "+active.token()).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\""+PASSWORD+"\"}")).hasStatusOk().bodyJson().hasPathSatisfying("$.code",v->v.assertThat().isEqualTo(0));
        var after=sessionRows.selectById(active.sessionId());assertThat(after.getLocked()).isFalse();assertThat(after.getExpiresAt()).isEqualTo(before.getExpiresAt());assertThat(after.getMfaVerifiedAt()).isEqualTo(before.getMfaVerifiedAt());
        assertThat(sessionQuery.active(active.sessionId(),f.user().id(),"admin")).isTrue();
        after.setExpiresAt(Instant.now().minusSeconds(1));sessionRows.updateById(after);
        assertThat(mvc.post().uri("/api/auth/unlock").header("Authorization","Bearer "+active.token()).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\""+PASSWORD+"\"}")).hasStatus(401);
    }
    @Test void passwordChangeBetweenStepsRejectsOldProofAndDoesNotRemoveTotp()throws Exception{
        var f=fixture();String secret=bind(f);var challenge=first(f);users.resetPassword(f.user().id(),"Q7fc1401e1");
        assertThatThrownBy(()->mfa.verify(challenge.challengeToken(),code(secret,0),"changed-"+f.user().id(),"test")).isInstanceOf(BizException.class);
        assertThat(totpRows.selectOne(new LambdaQueryWrapper<TotpCredentialEntity>().eq(TotpCredentialEntity::getUserId,f.user().id())).getEnabled()).isTrue();
        assertThat(sessions.resolve(f.token())).isEmpty();
    }
    @Test void superAdministratorCannotUsePasswordOnlyIssuerAfterBinding()throws Exception{
        var original=fixture();var row=userRows.selectById(original.user().id());row.setSuperAdmin(true);userRows.updateById(row);
        var administrator=users.authenticate(original.user().username(),PASSWORD,"admin").orElseThrow();
        var f=new Fixture(administrator,original.token(),original.sessionId());bind(f);
        assertThat(f.user().superAdmin()).isTrue();
        assertThat(first(f).status()).isEqualTo("MFA_REQUIRED");
        assertThatThrownBy(()->issuer.issue(f.user(),false,Duration.ofHours(8),"admin-ip","old-entry")).isInstanceOf(BizException.class);
        assertThat(sessions.resolve(f.token())).isEmpty();
    }
    @Test void consumedTimeStepCannotBeUsedOnAnotherChallengeAndWrongTenantCannotConsume()throws Exception{
        var f=fixture();String secret=bind(f);var one=first(f);var two=first(f);String otp=code(secret,0);
        TenantScope.within(920L,()->{assertThatThrownBy(()->mfa.verify(one.challengeToken(),otp,"tenant-probe","test")).isInstanceOf(BizException.class);return null;});
        assertThat(mfa.verify(one.challengeToken(),otp,"replay-"+f.user().id(),"test").token()).isNotBlank();
        assertThatThrownBy(()->mfa.verify(two.challengeToken(),otp,"replay-"+f.user().id(),"test")).isInstanceOf(BizException.class);
    }
    @Test void expiredAndForgedChallengesNeverIssueSessions()throws Exception{
        var f=fixture();String secret=bind(f);var login=first(f);var row=challengeRows.selectOne(new LambdaQueryWrapper<SecurityChallengeEntity>().eq(SecurityChallengeEntity::getTokenHash,Tokens.hash(login.challengeToken())));
        row.setExpiresAt(testTime.minusSeconds(1));challengeRows.updateById(row);
        assertThatThrownBy(()->mfa.verify(login.challengeToken(),code(secret,0),"expire-"+f.user().id(),"test")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->mfa.verify("A".repeat(43),code(secret,0),"forge","test")).isInstanceOf(BizException.class);
    }
    @Test void bindingMustBeConfirmedAndPendingReplacementDoesNotDisableOldFactor()throws Exception{
        var f=fixture();var pending=as(f,()->enrollment.begin(f.sessionId(),PASSWORD,null,"pending-"+f.user().id()));
        assertThat(first(f).status()).isEqualTo("AUTHENTICATED");
        as(f,()->{enrollment.cancel(f.sessionId(),pending.challengeToken());return null;});
        assertThatThrownBy(()->as(f,()->{enrollment.confirm(f.sessionId(),pending.challengeToken(),"123456");return null;})).isInstanceOf(BizException.class);
        String secret=bind(f);var login=mfa.verify(first(f).challengeToken(),code(secret,0),"pending-"+f.user().id(),"test");var active=completed(f,login.token());
        String future=code(secret,1);
        var replacement=as(active,()->enrollment.begin(active.sessionId(),PASSWORD,future,"pending-"+f.user().id()));
        assertThat(replacement.replacing()).isTrue();assertThat(sessions.resolve(active.token())).isPresent();
        var row=totpRows.selectOne(new LambdaQueryWrapper<TotpCredentialEntity>().eq(TotpCredentialEntity::getUserId,f.user().id()));assertThat(row.getEnabled()).isTrue();
        as(active,()->{enrollment.cancel(active.sessionId(),replacement.challengeToken());return null;});
        assertThat(sessions.resolve(active.token())).isPresent();
    }
    @Test void disablingRequiresPasswordAndCurrentTotpAndRevokesAllSessions()throws Exception{
        var f=fixture();String secret=bind(f);var active=completed(f,mfa.verify(first(f).challengeToken(),code(secret,0),"disable-"+f.user().id(),"test").token());
        assertThatThrownBy(()->as(active,()->{enrollment.disable(active.sessionId(),PASSWORD,null,"disable-"+f.user().id());return null;})).isInstanceOf(BizException.class);
        String future=code(secret,1);as(active,()->{enrollment.disable(active.sessionId(),PASSWORD,future,"disable-"+f.user().id());return null;});
        assertThat(sessions.resolve(active.token())).isEmpty();assertThat(first(f).status()).isEqualTo("AUTHENTICATED");
        var row=totpRows.selectOne(new LambdaQueryWrapper<TotpCredentialEntity>().eq(TotpCredentialEntity::getUserId,f.user().id()));assertThat(row.getSecretCiphertext()).isNull();assertThat(row.getEnabled()).isFalse();
    }
    @Test void httpSecondStepIsExplicitAndNeverReturnsACacheableToken()throws Exception{
        var f=fixture();String secret=bind(f);var challenge=first(f);
        var response=mvc.post().uri("/api/auth/login-challenges/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("challengeToken",challenge.challengeToken(),"code",code(secret,0)))).exchange();
        assertThat(response).hasStatusOk().bodyJson().hasPathSatisfying("$.data.status",v->v.assertThat().isEqualTo("AUTHENTICATED"));
        assertThat(response.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        String token=json.readTree(response.getResponse().getContentAsString()).path("data").path("token").asString();
        assertThat(mvc.get().uri("/api/auth/security-settings").header("Authorization","Bearer "+token)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data.totpEnabled",v->v.assertThat().isEqualTo(true)).doesNotHavePath("$.data.secret");
    }
    @Test void accountBudgetCannotBeResetByNewChallengeOrChangedIp()throws Exception{
        var f=fixture();String secret=bind(f);var one=first(f);var two=first(f);
        String good=code(secret,0),wrong=String.format(Locale.ROOT,"%06d",(Integer.parseInt(good)+123)%1000000);
        for(int i=0;i<5;i++){
            String token=i<3?one.challengeToken():two.challengeToken();String ip="vary-"+f.user().id()+"-"+i;
            assertThatThrownBy(()->mfa.verify(token,wrong,ip,"test")).isInstanceOf(BizException.class);
        }
        var third=first(f);
        assertThatThrownBy(()->mfa.verify(third.challengeToken(),good,"other-"+f.user().id(),"test"))
                .isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.RATE_LIMITED));
    }
    @Test void confirmedReplacementRevokesOldSessionsAndReplacesSecretAtomically()throws Exception{
        var f=fixture();String original=bind(f);var active=completed(f,mfa.verify(first(f).challengeToken(),code(original,0),"replace-"+f.user().id(),"test").token());
        String proof=code(original,1);var pending=as(active,()->enrollment.begin(active.sessionId(),PASSWORD,proof,"replace-"+f.user().id()));
        String newProof=code(pending.secret(),0);as(active,()->{confirmation.confirm(active.sessionId(),pending.challengeToken(),newProof,"replace-"+f.user().id());return null;});
        assertThat(sessions.resolve(active.token())).isEmpty();
        var login=mfa.verify(first(f).challengeToken(),code(pending.secret(),1),"replace-"+f.user().id(),"test");
        assertThat(sessions.resolve(login.token())).isPresent();
    }
    @Test void expiredPendingMaterialIsErasedWithoutDisablingAnExistingFactor()throws Exception{
        var f=fixture();var pending=as(f,()->enrollment.begin(f.sessionId(),PASSWORD,null,"cleanup-"+f.user().id()));
        var row=totpRows.selectOne(new LambdaQueryWrapper<TotpCredentialEntity>().eq(TotpCredentialEntity::getUserId,f.user().id()));
        row.setPendingExpiresAt(Instant.now().minusSeconds(1));totpRows.updateById(row);maintenance.expire();
        row=totpRows.selectById(row.getId());assertThat(row.getPendingSecretCiphertext()).isNull();assertThat(sessions.resolve(f.token())).isPresent();
        assertThatThrownBy(()->as(f,()->{enrollment.confirm(f.sessionId(),pending.challengeToken(),"123456");return null;})).isInstanceOf(BizException.class);
    }
}
