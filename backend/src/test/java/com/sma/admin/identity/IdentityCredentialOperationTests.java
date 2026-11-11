package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sma.admin.identity.EmailIdentityTestSupport.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class IdentityCredentialOperationTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-credential-operations-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityEmailConfigurationService configurations;
    @Autowired MailProfileService profiles;@Autowired MailTemplateService templates;@Autowired IdentityEmailAccountService accounts;
    @Autowired IdentityEmailChallengeStore challenges;@Autowired IdentityEmailChallengeMapper challengeRows;@Autowired MailDeliveryMapper mailRows;@Autowired MailOutboxService outbox;
    @Autowired IdentitySessionStore sessions;@Autowired IdentityAuthority authority;@Autowired IdentityCredentialOperations operations;
    @Autowired IdentityPasswordReauthenticationCoordinator reauthentication;@Autowired IdentityReauthenticationStore grants;
    @Autowired IdentityPasswordLoginCoordinator passwords;@Autowired IdentityExternalMapper external;@Autowired IdentityEmailCredentialMapper credentials;
    @Autowired TransactionTemplate tx;@MockitoSpyBean IdentityClock clock;@MockitoSpyBean MailWorkClock mailClock;
    private Instant now;private Mailbox mailbox;private final String password="Q7d4e10594";
    private record Account(IdentitySessionStore.Issued tokens,AppUserPrincipal actor){}
    @BeforeEach void setup(){now=Instant.parse("2026-11-12T00:00:00Z");doAnswer(i->now).when(clock).now();doAnswer(i->now).when(mailClock).now();mailbox=new Mailbox(accounts,challenges,challengeRows,mailRows,outbox);}
    private Fixture fixture(long tenant){return EmailIdentityTestSupport.fixture(tenant,realms,clients,configurations,profiles,templates);}
    private Account actor(Fixture f,IdentitySessionStore.Issued tokens){return new Account(tokens,job(f.tenant(),()->sessions.authenticate(tokens.accessToken(),f.client().loginHandle(),"WEB").principal()));}
    private Account register(Fixture f,String email){return actor(f,mailbox.register(f,email,password));}
    private Account passwordLogin(Fixture f,String email){return actor(f,job(f.tenant(),()->passwords.login(new IdentityPasswordLoginCoordinator.Login(f.client().loginHandle(),email,password,ORIGIN,"10.0.0.1"))));}
    /** Synthetic verified provider fixture; the full RSA/nonce/operation path is covered by IdentityGoogleOperationTests. */
    private Account google(Fixture f,String sub){return actor(f,job(f.tenant(),()->tx.execute(status->sessions.google(authority.capture(f.client().loginHandle()),new GoogleIdTokenVerifier.Verified("https://accounts.google.com",sub,null,false)))));}
    private Issued passwordProof(Fixture f,Account actor,Action action,String target){
        return job(f.tenant(),()->reauthentication.verify(new IdentityPasswordReauthenticationCoordinator.Request(actor.actor,action,target,password,ORIGIN,"10.0.0.1")));
    }
    private Issued googleProof(Fixture f,Account account,Action action,String target){return job(f.tenant(),()->tx.execute(status->{
        var stamp=sessions.currentActor(account.actor);return grants.issue(account.actor,stamp,action,grants.target(stamp,account.actor,action,target),Method.GOOGLE);
    }));}
    private Use use(Issued grant,Action action,String target){return new Use(grant.grant(),action,target);}
    private IdentityEmailDeliveryService.Issued start(Fixture f,Account account,Issued grant,String email){
        return job(f.tenant(),()->operations.beginBinding(account.actor,new IdentityCredentialOperations.Binding(use(grant,Action.EMAIL_BINDING,email),"en-US",email,ORIGIN,"10.0.0.1")));
    }
    private IdentityEmailChallengeStore.Use binding(Fixture f,Account account,String email,boolean google){
        var proof=google?googleProof(f,account,Action.EMAIL_BINDING,email):passwordProof(f,account,Action.EMAIL_BINDING,email);
        return mailbox.verify(f,start(f,account,proof,email),IdentityEmailChallengeStore.Purpose.EMAIL_BINDING);
    }
    private IdentityEmailCredentialEntity credential(Fixture f,Account account){return job(f.tenant(),()->credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getSubjectId,account.actor.subjectId())));}
    private Long link(Fixture f,Account account){return job(f.tenant(),()->external.selectOne(new LambdaQueryWrapper<IdentityExternalEntity>().eq(IdentityExternalEntity::getSubjectId,account.actor.subjectId()).last("LIMIT 1")).getId());}
    private void denied(Runnable work,IdentityErrorCode code){assertThatThrownBy(work::run).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(code));}
    private void invalidSession(Fixture f,Account account){denied(()->job(f.tenant(),()->sessions.authenticate(account.tokens.accessToken(),f.client().loginHandle(),"WEB")),IdentityErrorCode.INVALID_SESSION);}

    @Test void originalProofAndMailboxProofAreBothRequiredAndTargetCannotChange(){
        var f=fixture(1261);var account=register(f,"owner@example.test");var proof=passwordProof(f,account,Action.EMAIL_BINDING,"new@example.test");
        denied(()->job(f.tenant(),()->operations.beginBinding(account.actor,new IdentityCredentialOperations.Binding(use(proof,Action.EMAIL_BINDING,"new@example.test"),"en-US","other@example.test",ORIGIN,"10.0.0.1"))),IdentityErrorCode.INVALID_REAUTHENTICATION);
        var issued=start(f,account,proof,"new@example.test");
        denied(()->start(f,account,proof,"new@example.test"),IdentityErrorCode.INVALID_REAUTHENTICATION);
        now=now.plusSeconds(61);
        var reset=mailbox.grant(f,"owner@example.test",IdentityEmailChallengeStore.Purpose.PASSWORD_RESET);
        denied(()->job(f.tenant(),()->operations.completeBinding(account.actor,reset,null)),IdentityErrorCode.INVALID_REAUTHENTICATION);
        var verified=mailbox.verify(f,issued,IdentityEmailChallengeStore.Purpose.EMAIL_BINDING);
        var changed=job(f.tenant(),()->operations.completeBinding(account.actor,verified,null));assertThat(changed.signInRequired()).isTrue();invalidSession(f,account);
        assertThat(passwordLogin(f,"new@example.test").actor.subjectId()).isEqualTo(account.actor.subjectId());
        denied(()->passwordLogin(f,"owner@example.test"),IdentityErrorCode.INVALID_PASSWORD_LOGIN);
    }
    @Test void boundMailboxGrantCannotMoveAcrossSubjectSessionClientOrTenant(){
        var f=fixture(1262);var owner=register(f,"owner@example.test");var other=register(f,"other@example.test");var secondSession=passwordLogin(f,"owner@example.test");
        var use=binding(f,owner,"new@example.test",false);
        for(var wrong:List.of(other,secondSession))denied(()->job(f.tenant(),()->operations.completeBinding(wrong.actor,use,null)),IdentityErrorCode.INVALID_REAUTHENTICATION);
        var foreignFixture=fixture(1263);var foreign=register(foreignFixture,"owner@example.test");
        assertThatThrownBy(()->job(foreignFixture.tenant(),()->operations.completeBinding(foreign.actor,use,null))).isInstanceOf(BizException.class);
        var client=admin(f.tenant(),()->clients.create(new IdentityClientService.Input(f.realm().id(),"other","Other","other",IdentityClientService.Platform.WEB,true,false,null,null,Set.of(ORIGIN+"/cb"),Set.of(ORIGIN),900,2592000)));
        var another=job(f.tenant(),()->passwords.login(new IdentityPasswordLoginCoordinator.Login(client.loginHandle(),"owner@example.test",password,ORIGIN,"10.0.0.1")));
        var wrongClient=job(f.tenant(),()->sessions.authenticate(another.accessToken(),client.loginHandle(),"WEB").principal());
        denied(()->job(f.tenant(),()->operations.completeBinding(wrongClient,use,null)),IdentityErrorCode.INVALID_REAUTHENTICATION);
        assertThat(job(f.tenant(),()->operations.completeBinding(owner.actor,use,null)).changed()).isTrue();
    }
    @Test void emailConflictAndExistingPasswordCannotBeOverwrittenByBinding(){
        var f=fixture(1264);var owner=register(f,"owner@example.test");var other=register(f,"taken@example.test");
        now=now.plusSeconds(61);var conflicting=binding(f,owner,"taken@example.test",false);
        denied(()->job(f.tenant(),()->operations.completeBinding(owner.actor,conflicting,null)),IdentityErrorCode.IDENTITY_LINK_CONFLICT);
        var grant=binding(f,owner,"new@example.test",false);String originalHash=credential(f,owner).getPasswordHash();
        denied(()->job(f.tenant(),()->operations.completeBinding(owner.actor,grant,"K7n905d156")),IdentityErrorCode.PASSWORD_ALREADY_CONFIGURED);
        assertThat(job(f.tenant(),()->operations.completeBinding(owner.actor,grant,null)).changed()).isTrue();
        assertThat(credential(f,owner).getPasswordHash()).isEqualTo(originalHash);assertThat(credential(f,other).getEmailVersion()).isEqualTo(1);
        assertThat(passwordLogin(f,"new@example.test").actor.subjectId()).isEqualTo(owner.actor.subjectId());
    }
    @Test void googleOnlyExplicitBindingAddsPasswordButContactOnlyAndResetDoNot(){
        var f=fixture(1265);var account=google(f,"owner");var contact=binding(f,account,"contact@example.test",true);
        assertThat(job(f.tenant(),()->operations.completeBinding(account.actor,contact,null)).changed()).isTrue();assertThat(credential(f,account).getPasswordHash()).isNull();invalidSession(f,account);
        now=now.plusSeconds(61);var reset=mailbox.grant(f,"contact@example.test",IdentityEmailChallengeStore.Purpose.PASSWORD_RESET);
        denied(()->job(f.tenant(),()->{accounts.resetPassword(reset,password);return null;}),IdentityErrorCode.INVALID_EMAIL_CHALLENGE);
        now=now.plusSeconds(61);var renewed=google(f,"owner");var add=binding(f,renewed,"contact@example.test",true);
        assertThat(job(f.tenant(),()->operations.completeBinding(renewed.actor,add,password)).signInRequired()).isTrue();
        assertThat(credential(f,renewed).getPasswordHash()).startsWith("$2");assertThat(passwordLogin(f,"contact@example.test").actor.subjectId()).isEqualTo(renewed.actor.subjectId());
    }
    @Test void lastLoginMethodIsProtectedAndVerifiedContactWithoutPasswordDoesNotCount(){
        var f=fixture(1266);var account=register(f,"password@example.test");var proof=passwordProof(f,account,Action.PASSWORD_REMOVAL,"password");
        denied(()->job(f.tenant(),()->operations.removePassword(account.actor,use(proof,Action.PASSWORD_REMOVAL,"password"))),IdentityErrorCode.LAST_AUTHENTICATION_METHOD);
        assertThat(credential(f,account).getPasswordHash()).isNotNull();
        var google=google(f,"google-owner");var contact=binding(f,google,"google@example.test",true);job(f.tenant(),()->operations.completeBinding(google.actor,contact,null));
        var renewed=google(f,"google-owner");String link=link(f,renewed).toString();var remove=googleProof(f,renewed,Action.GOOGLE_REMOVAL,link);
        denied(()->job(f.tenant(),()->operations.removeGoogle(renewed.actor,use(remove,Action.GOOGLE_REMOVAL,link))),IdentityErrorCode.LAST_AUTHENTICATION_METHOD);
        assertThat(job(f.tenant(),()->external.selectCount(null))).isEqualTo(1);
    }
    @Test void removePasswordPreservesVerifiedContactAndRemoveGooglePreservesHistoricalOwnership(){
        var f=fixture(1267);var account=google(f,"owner");var binding=binding(f,account,"owner@example.test",true);job(f.tenant(),()->operations.completeBinding(account.actor,binding,password));
        var renewed=passwordLogin(f,"owner@example.test");var before=credential(f,renewed);var proof=passwordProof(f,renewed,Action.PASSWORD_REMOVAL,"password");
        assertThat(job(f.tenant(),()->operations.removePassword(renewed.actor,use(proof,Action.PASSWORD_REMOVAL,"password"))).signInRequired()).isTrue();invalidSession(f,renewed);
        var after=credential(f,renewed);assertThat(after.getPasswordHash()).isNull();assertThat(after.getEmailEnvelope()).isEqualTo(before.getEmailEnvelope());assertThat(after.getEmailVerifiedAt()).isEqualTo(before.getEmailVerifiedAt());
        denied(()->passwordLogin(f,"owner@example.test"),IdentityErrorCode.INVALID_PASSWORD_LOGIN);
        now=now.plusSeconds(61);var google=google(f,"owner");var readd=binding(f,google,"owner@example.test",true);job(f.tenant(),()->operations.completeBinding(google.actor,readd,password));
        var active=passwordLogin(f,"owner@example.test");String id=link(f,active).toString();var remove=passwordProof(f,active,Action.GOOGLE_REMOVAL,id);
        assertThat(job(f.tenant(),()->operations.removeGoogle(active.actor,use(remove,Action.GOOGLE_REMOVAL,id))).signInRequired()).isTrue();invalidSession(f,active);
        denied(()->google(f,"owner"),IdentityErrorCode.REVOKED_IDENTITY_LINK);assertThat(passwordLogin(f,"owner@example.test").actor.subjectId()).isEqualTo(active.actor.subjectId());
    }
    @Test void creationAndCredentialMutationsRollbackTogetherWithTheirProofConsumption(){
        var f=fixture(1268);var owner=register(f,"owner@example.test");var proof=passwordProof(f,owner,Action.EMAIL_BINDING,"new@example.test");
        assertThatThrownBy(()->job(f.tenant(),()->operations.beginBinding(owner.actor,new IdentityCredentialOperations.Binding(use(proof,Action.EMAIL_BINDING,"new@example.test"),"invalid-language","new@example.test",ORIGIN,"10.0.0.1")))).isInstanceOf(BizException.class);
        var use=mailbox.verify(f,start(f,owner,proof,"new@example.test"),IdentityEmailChallengeStore.Purpose.EMAIL_BINDING);var before=credential(f,owner);
        assertThatThrownBy(()->job(f.tenant(),()->tx.execute(status->{operations.completeBinding(owner.actor,use,null);throw new IllegalStateException("domain rollback");}))).isInstanceOf(IllegalStateException.class);
        assertThat(credential(f,owner).getEmailEnvelope()).isEqualTo(before.getEmailEnvelope());
        assertThat(job(f.tenant(),()->sessions.authenticate(owner.tokens.accessToken(),f.client().loginHandle(),"WEB").principal())).isEqualTo(owner.actor);
        assertThat(job(f.tenant(),()->operations.completeBinding(owner.actor,use,null)).changed()).isTrue();
    }
    @Test void originalSessionRevocationAndConfigurationChangeInvalidateBindingBeforeMutation(){
        var f=fixture(1269);var account=register(f,"owner@example.test");var use=binding(f,account,"new@example.test",false);
        job(f.tenant(),()->{sessions.revokeSubject(account.actor.subjectId());return null;});
        assertThatThrownBy(()->job(f.tenant(),()->operations.completeBinding(account.actor,use,null))).isInstanceOf(BizException.class);
        var refreshed=passwordLogin(f,"owner@example.test");var another=binding(f,refreshed,"another@example.test",false);admin(f.tenant(),()->clients.disable(f.client().id(),f.client().version()));
        assertThatThrownBy(()->job(f.tenant(),()->operations.completeBinding(refreshed.actor,another,null))).isInstanceOf(BizException.class);
        assertThat(credential(f,refreshed).getEmailVersion()).isEqualTo(1);
    }
    @Test void concurrentRemovalsCannotDeleteBothRemainingMethods()throws Exception{
        var f=fixture(1270);var account=google(f,"owner");var binding=binding(f,account,"owner@example.test",true);job(f.tenant(),()->operations.completeBinding(account.actor,binding,password));
        var active=passwordLogin(f,"owner@example.test");String id=link(f,active).toString();
        var passwordGrant=passwordProof(f,active,Action.PASSWORD_REMOVAL,"password");var googleGrant=passwordProof(f,active,Action.GOOGLE_REMOVAL,id);
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Boolean> removePassword=()->{gate.await();try{job(f.tenant(),()->operations.removePassword(active.actor,use(passwordGrant,Action.PASSWORD_REMOVAL,"password")));return true;}catch(BizException rejected){return false;}};
            Callable<Boolean> removeGoogle=()->{gate.await();try{job(f.tenant(),()->operations.removeGoogle(active.actor,use(googleGrant,Action.GOOGLE_REMOVAL,id)));return true;}catch(BizException rejected){return false;}};
            var a=pool.submit(removePassword);var b=pool.submit(removeGoogle);gate.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        long remaining=job(f.tenant(),()->external.selectCount(null))+(credential(f,active).getPasswordHash()==null?0:1);assertThat(remaining).isEqualTo(1);
    }
}
