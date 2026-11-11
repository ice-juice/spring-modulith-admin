package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sma.admin.identity.EmailIdentityTestSupport.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.identity.internal.service.IdentityReauthenticationStore.*;
import com.sma.admin.mail.internal.persistence.MailDeliveryMapper;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.IntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.*;

@IntegrationTest
class IdentityReauthenticationTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] value=new byte[32];new SecureRandom().nextBytes(value);keyFile=Files.createTempFile("sma-reauthentication-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(value));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
        registry.add("sma.identity.reauthentication.maximum-per-subject",()->2);registry.add("sma.identity.reauthentication.maximum-per-realm",()->3);
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityEmailConfigurationService configurations;
    @Autowired MailProfileService profiles;@Autowired MailTemplateService templates;@Autowired IdentityEmailAccountService accounts;
    @Autowired IdentityEmailChallengeStore challenges;@Autowired IdentityEmailChallengeMapper challengeRows;@Autowired MailDeliveryMapper mailRows;@Autowired MailOutboxService outbox;
    @Autowired IdentitySessionStore sessions;@Autowired IdentitySubjectMapper subjects;@Autowired IdentityReauthenticationMapper rows;
    @Autowired IdentityReauthenticationStore grants;@Autowired IdentityPasswordReauthenticationCoordinator reauthentication;@Autowired IdentityPasswordReauthenticationStore proofs;
    @Autowired IdentityPasswordLoginCoordinator login;@Autowired TransactionTemplate tx;
    @MockitoSpyBean IdentityClock clock;@MockitoSpyBean MailWorkClock mailClock;@MockitoSpyBean IdentityPasswordWork passwords;
    private Instant now;private Mailbox mailbox;private final String password="Q7d4e10594";
    @BeforeEach void setup(){
        now=Instant.parse("2026-11-12T00:00:00.123456789Z");doAnswer(i->now).when(clock).now();doAnswer(i->now).when(mailClock).now();
        mailbox=new Mailbox(accounts,challenges,challengeRows,mailRows,outbox);
        doAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return i.callRealMethod();}).when(passwords).matches(anyString(),nullable(String.class));
    }
    private Fixture fixture(long tenant){return EmailIdentityTestSupport.fixture(tenant,realms,clients,configurations,profiles,templates);}
    private AppUserPrincipal actor(Fixture f,IdentitySessionStore.Issued issued){return job(f.tenant(),()->sessions.authenticate(issued.accessToken(),f.client().loginHandle(),"WEB").principal());}
    private AppUserPrincipal register(Fixture f,String email){return actor(f,mailbox.register(f,email,password));}
    private Issued reauth(Fixture f,AppUserPrincipal actor,Action action,String target,String password){
        return job(f.tenant(),()->reauthentication.verify(new IdentityPasswordReauthenticationCoordinator.Request(actor,action,target,password,ORIGIN,"10.0.0.1")));
    }
    private <T>T app(AppUserPrincipal actor,Supplier<T> action){
        var old=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of()));
        SecurityContextHolder.setContext(context);try{return action.get();}finally{SecurityContextHolder.setContext(old);}
    }
    private List<IdentityReauthenticationEntity> rows(long tenant){return job(tenant,()->rows.selectList(new LambdaQueryWrapper<IdentityReauthenticationEntity>().orderByAsc(IdentityReauthenticationEntity::getId)));}
    private Use use(Issued grant,Action action,String target){return new Use(grant.grant(),action,target);}
    @Test void explicitPasswordProofBindsCanonicalTargetAndConsumptionRollsBackWithDomainWork(){
        var f=fixture(1221);var actor=register(f,"owner@example.test");var issued=reauth(f,actor,Action.EMAIL_BINDING,"New@EXAMPLE.TEST",password);
        assertThat(issued.grant()).hasSize(43);var row=rows(f.tenant()).getFirst();assertThat(row.getGrantHash()).isNotEqualTo(issued.grant());
        assertThat(row.getTargetRef()).matches("[0-9a-f]{64}").doesNotContain("New@");assertThat(row.getMethod()).isEqualTo("PASSWORD");
        for(var wrong:List.of(use(issued,Action.EMAIL_BINDING,"new@example.test"),use(issued,Action.PASSWORD_REMOVAL,"password")))
            assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->grants.consume(actor,wrong,verified->true)))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->grants.consume(actor,use(issued,Action.EMAIL_BINDING,"New@example.test"),verified->{
            var subject=subjects.selectById(actor.subjectId());subject.setEnabled(false);subjects.updateById(subject);throw new IllegalStateException("domain rollback");
        })))).isInstanceOf(IllegalStateException.class);
        assertThat(rows(f.tenant()).getFirst().getStatus()).isEqualTo("ACTIVE");assertThat(job(f.tenant(),()->subjects.selectById(actor.subjectId())).getEnabled()).isTrue();
        Boolean accepted=job(f.tenant(),()->tx.execute(status->grants.consume(actor,use(issued,Action.EMAIL_BINDING,"New@example.test"),verified->verified.method()==Method.PASSWORD)));
        assertThat(accepted).isTrue();assertThat(rows(f.tenant()).getFirst().getGrantHash()).isNull();
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->grants.consume(actor,use(issued,Action.EMAIL_BINDING,"New@example.test"),verified->true)))).isInstanceOf(BizException.class);
    }
    @Test void grantCannotMoveToAnotherSessionSubjectClientOrTenant(){
        var f=fixture(1222);var actor=register(f,"first@example.test");var secondSubject=register(f,"second@example.test");var issued=reauth(f,actor,Action.GOOGLE_BINDING,"google",password);
        var secondSession=actor(f,job(f.tenant(),()->login.login(new IdentityPasswordLoginCoordinator.Login(f.client().loginHandle(),"first@example.test",password,ORIGIN,"10.0.0.2"))));
        var other=fixture(1223);var foreign=register(other,"other@example.test");
        for(var wrong:List.of(secondSubject,secondSession,foreign))assertThatThrownBy(()->job(wrong.tenantId(),()->tx.execute(status->grants.consume(wrong,use(issued,Action.GOOGLE_BINDING,"google"),v->true)))).isInstanceOf(BizException.class);
        var anotherClient=admin(f.tenant(),()->clients.create(new IdentityClientService.Input(f.realm().id(),"other","Other","other",IdentityClientService.Platform.WEB,true,false,null,null,Set.of(ORIGIN+"/cb"),Set.of(ORIGIN),900,2592000)));
        var token=job(f.tenant(),()->login.login(new IdentityPasswordLoginCoordinator.Login(anotherClient.loginHandle(),"first@example.test",password,ORIGIN,"10.0.0.3")));
        var clientActor=job(f.tenant(),()->sessions.authenticate(token.accessToken(),anotherClient.loginHandle(),"WEB").principal());
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->grants.consume(clientActor,use(issued,Action.GOOGLE_BINDING,"google"),v->true)))).isInstanceOf(BizException.class);
        Boolean accepted=job(f.tenant(),()->tx.execute(status->grants.consume(actor,use(issued,Action.GOOGLE_BINDING,"google"),v->true)));assertThat(accepted).isTrue();
    }
    @Test void wrongPasswordBudgetCommitsAndIsSharedWithOrdinaryLogin(){
        var f=fixture(1224);var actor=register(f,"budget@example.test");
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->{proofs.prepare(new IdentityPasswordReauthenticationStore.Attempt(actor,Action.GOOGLE_BINDING,"google",ORIGIN,"10.0.0.1"));throw new IllegalStateException("outer rollback");})))
                .isInstanceOf(IllegalStateException.class);
        for(int i=0;i<4;i++)assertThatThrownBy(()->reauth(f,actor,Action.GOOGLE_BINDING,"google","wrongpass1")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_PASSWORD_LOGIN));
        assertThatThrownBy(()->job(f.tenant(),()->login.login(new IdentityPasswordLoginCoordinator.Login(f.client().loginHandle(),"budget@example.test",password,ORIGIN,"10.0.0.9"))))
                .isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.EMAIL_CHALLENGE_LIMIT));
        assertThat(rows(f.tenant())).isEmpty();
    }
    @Test void logoutDuringPasswordCheckAndLogoutAfterIssuingBothInvalidateAuthorization(){
        var f=fixture(1225);var actor=register(f,"logout@example.test");var issued=reauth(f,actor,Action.GOOGLE_BINDING,"google",password);
        var prepared=job(f.tenant(),()->proofs.prepare(new IdentityPasswordReauthenticationStore.Attempt(actor,Action.EMAIL_BINDING,"new@example.test",ORIGIN,"10.0.0.1")));
        assertThat(passwords.matches(password,prepared.passwordHash())).isTrue();app(actor,()->{sessions.logout();return null;});
        assertThatThrownBy(()->job(f.tenant(),()->proofs.finish(prepared))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->grants.consume(actor,use(issued,Action.GOOGLE_BINDING,"google"),v->true)))).isInstanceOf(BizException.class);
        var newActor=actor(f,job(f.tenant(),()->login.login(new IdentityPasswordLoginCoordinator.Login(f.client().loginHandle(),"logout@example.test",password,ORIGIN,"10.0.0.1"))));
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->grants.consume(newActor,use(issued,Action.GOOGLE_BINDING,"google"),v->true)))).isInstanceOf(BizException.class);
    }
    @Test void expiryCapacityAndMaintenanceKeepReauthenticationStateBounded(){
        var f=fixture(1226);var first=register(f,"one@example.test");var second=register(f,"two@example.test");
        var a=reauth(f,first,Action.GOOGLE_BINDING,"google",password);reauth(f,first,Action.EMAIL_BINDING,"next@example.test",password);
        assertThatThrownBy(()->reauth(f,first,Action.PASSWORD_REMOVAL,"password",password)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.REAUTHENTICATION_LIMIT));
        reauth(f,second,Action.GOOGLE_BINDING,"google",password);
        assertThatThrownBy(()->reauth(f,second,Action.EMAIL_BINDING,"other@example.test",password)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.REAUTHENTICATION_LIMIT));
        assertThat(rows(f.tenant())).hasSize(3);now=now.plusSeconds(121);
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->grants.consume(first,use(a,Action.GOOGLE_BINDING,"google"),v->true)))).isInstanceOf(BizException.class);
        job(f.tenant(),grants::maintain);assertThat(rows(f.tenant())).allSatisfy(row->{assertThat(row.getStatus()).isEqualTo("EXPIRED");assertThat(row.getGrantHash()).isNull();});
        now=now.plusSeconds(601);job(f.tenant(),grants::maintain);assertThat(rows(f.tenant())).isEmpty();assertThat(reauth(f,first,Action.GOOGLE_BINDING,"google",password).grant()).hasSize(43);
    }
    @Test void concurrentConsumptionHasOneWinnerAndConfigurationChangesRejectUnusedGrants()throws Exception{
        var f=fixture(1227);var actor=register(f,"race@example.test");var grant=reauth(f,actor,Action.GOOGLE_BINDING,"google",password);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);var results=new ArrayList<Future<Boolean>>();
        try{
            for(int i=0;i<2;i++)results.add(pool.submit(()->{start.await();try{return job(f.tenant(),()->tx.execute(status->grants.consume(actor,use(grant,Action.GOOGLE_BINDING,"google"),v->true)));}catch(BizException rejected){return false;}}));
            start.countDown();int accepted=0;for(var result:results)if(result.get(30,TimeUnit.SECONDS))accepted++;assertThat(accepted).isEqualTo(1);
        }finally{pool.shutdownNow();}
        var unused=reauth(f,actor,Action.EMAIL_BINDING,"new@example.test",password);admin(f.tenant(),()->clients.disable(f.client().id(),f.client().version()));
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->grants.consume(actor,use(unused,Action.EMAIL_BINDING,"new@example.test"),v->true)))).isInstanceOf(BizException.class);
    }
}
