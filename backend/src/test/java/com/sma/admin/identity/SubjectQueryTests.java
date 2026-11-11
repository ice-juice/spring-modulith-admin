package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sma.admin.identity.EmailIdentityTestSupport.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.mail.internal.persistence.MailDeliveryMapper;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest @Import(SubjectQueryTests.Policies.class)
class SubjectQueryTests {
    @TestConfiguration(proxyBeanMethods=false) static class Policies {@Bean TestPolicy subjectPolicy(){return new TestPolicy();}}
    static class TestPolicy implements SubjectAccessPolicy {
        final Set<Content> allowed=EnumSet.noneOf(Content.class);ActorRef observed;
        public String application(){return "shop";}
        public boolean canRead(ActorRef actor,SubjectReadRequest request,Content content){observed=actor;return allowed.contains(content) && request.purpose().equals("notice");}
    }
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-subject-query-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired SubjectQuery query;@Autowired SubjectContactQuery contacts;@Autowired TestPolicy policy;
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityEmailConfigurationService configurations;
    @Autowired MailProfileService profiles;@Autowired MailTemplateService templates;@Autowired IdentityEmailAccountService accounts;
    @Autowired IdentityEmailChallengeStore challenges;@Autowired IdentityEmailChallengeMapper challengeRows;@Autowired MailDeliveryMapper mailRows;@Autowired MailOutboxService outbox;
    @Autowired IdentitySessionStore sessions;@Autowired IdentityAuthority authority;@Autowired IdentitySubjectMapper subjects;
    @Autowired TransactionTemplate tx;@MockitoSpyBean IdentityClock clock;@MockitoSpyBean MailWorkClock mailClock;@MockitoSpyBean TenantEntryQuery tenants;
    private Mailbox mailbox;private final Instant now=Instant.parse("2026-11-12T00:00:00Z");
    @BeforeEach void setup(){doReturn(now).when(clock).now();doReturn(now).when(mailClock).now();policy.allowed.clear();policy.observed=null;mailbox=new Mailbox(accounts,challenges,challengeRows,mailRows,outbox);}
    private Fixture fixture(long tenant){return EmailIdentityTestSupport.fixture(tenant,realms,clients,configurations,profiles,templates);}
    private AppUserPrincipal register(Fixture f,String address){var issued=mailbox.register(f,address,"Q7d4e10594");return job(f.tenant(),()->sessions.authenticate(issued.accessToken(),f.client().loginHandle(),"WEB").principal());}
    private SubjectReadRequest request(Fixture f,AppUserPrincipal actor){return new SubjectReadRequest("shop",f.realm().id(),actor.subjectId(),"notice");}
    private <T>T app(AppUserPrincipal actor,Supplier<T> work){var old=SecurityContextHolder.getContext();var scope=SecurityContextHolder.createEmptyContext();
        scope.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of()));SecurityContextHolder.setContext(scope);
        try{return TenantScope.within(actor.tenantId(),work);}finally{SecurityContextHolder.setContext(old);}}

    @Test void currentRequiresLiveApplicationIdentityAndDoesNotGrantContactRead(){
        var f=fixture(1341);var actor=register(f,"self@example.test");
        var view=app(actor,()->{var current=query.current("shop");assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.APP_USER);return current;});
        assertThat(view.subjectId()).isEqualTo(actor.subjectId());assertThat(view.tenantId()).isEqualTo(f.tenant());assertThat(view.enabled()).isTrue();
        assertThatThrownBy(()->app(actor,()->query.current("other"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->app(actor,()->contacts.verifiedEmail(request(f,actor)))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->query.current("shop")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->job(f.tenant(),()->query.current("shop"))).isInstanceOf(AccessDeniedException.class);
        job(f.tenant(),()->{sessions.revokeSubject(actor.subjectId());return null;});
        assertThatThrownBy(()->app(actor,()->query.current("shop"))).isInstanceOf(BizException.class);
    }
    @Test void purposeContentAndOriginalActorAreCheckedBeforeContactProjection(){
        var f=fixture(1342);var actor=register(f,"contact@example.test");var request=request(f,actor);policy.allowed.add(SubjectAccessPolicy.Content.BASIC);
        assertThat(app(actor,()->query.find(request))).isPresent();assertThat(policy.observed.kind()).isEqualTo(ActorRef.Kind.APP_USER);
        assertThatThrownBy(()->app(actor,()->contacts.verifiedEmail(request))).isInstanceOf(AccessDeniedException.class);
        policy.allowed.add(SubjectAccessPolicy.Content.VERIFIED_EMAIL);
        var contact=app(actor,()->contacts.verifiedEmail(request)).orElseThrow();assertThat(contact.email()).isEqualTo("contact@example.test");assertThat(contact.toString()).doesNotContain(contact.email());
        assertThatThrownBy(()->app(actor,()->contacts.verifiedEmail(new SubjectReadRequest("shop",actor.realmId(),actor.subjectId(),"unapproved")))).isInstanceOf(AccessDeniedException.class);
        var original=ActorContext.withinJob(f.tenant(),"owned-notice:42",()->{var result=contacts.verifiedEmail(request);assertThat(ActorContext.require().subject()).isEqualTo("owned-notice:42");return result;});
        assertThat(original).isPresent();assertThat(policy.observed.subject()).isEqualTo("owned-notice:42");
    }
    @Test void permissiveBusinessPolicyCannotCrossTenantRealmOrApplicationBounds(){
        var f=fixture(1343);var actor=register(f,"same@example.test");var other=fixture(1344);var foreign=register(other,"same@example.test");policy.allowed.addAll(EnumSet.allOf(SubjectAccessPolicy.Content.class));
        assertThat(job(f.tenant(),()->query.find(request(other,foreign)))).isEmpty();assertThat(job(f.tenant(),()->contacts.verifiedEmail(request(other,foreign)))).isEmpty();
        var realm=admin(f.tenant(),()->realms.create(new IdentityRealmService.Input("isolated","Isolated",true,true,true)));
        assertThat(job(f.tenant(),()->query.find(new SubjectReadRequest("shop",realm.id(),actor.subjectId(),"notice")))).isEmpty();
        assertThatThrownBy(()->app(actor,()->query.find(new SubjectReadRequest("shop",realm.id(),actor.subjectId(),"notice")))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->job(f.tenant(),()->query.find(new SubjectReadRequest("missing",actor.realmId(),actor.subjectId(),"notice")))).isInstanceOf(AccessDeniedException.class);
        admin(f.tenant(),()->clients.disable(f.client().id(),f.client().version()));assertThat(job(f.tenant(),()->query.find(request(f,actor)))).isEmpty();
        assertThat(job(other.tenant(),()->contacts.verifiedEmail(request(other,foreign)))).isPresent();
    }
    @Test void adminMachineAndJobsHaveNoImplicitContactPermission(){
        var f=fixture(1345);var actor=register(f,"owner@example.test");var request=request(f,actor);
        var admin=new SmaPrincipal(501,"super",f.tenant(),"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
        assertThatThrownBy(()->Users.as(admin,()->contacts.verifiedEmail(request))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->Users.as(SmaPrincipal.forApplication(f.tenant(),"admin","sender"),()->contacts.verifiedEmail(request))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->job(f.tenant(),()->contacts.verifiedEmail(request))).isInstanceOf(AccessDeniedException.class);
        policy.allowed.add(SubjectAccessPolicy.Content.VERIFIED_EMAIL);
        assertThat(Users.as(admin,()->contacts.verifiedEmail(request))).isPresent();assertThat(policy.observed.kind()).isEqualTo(ActorRef.Kind.ADMIN_USER);
        assertThat(Users.as(SmaPrincipal.forApplication(f.tenant(),"admin","sender"),()->contacts.verifiedEmail(request))).isPresent();assertThat(policy.observed.kind()).isEqualTo(ActorRef.Kind.APPLICATION);
        assertThatThrownBy(()->Users.as(admin,()->query.current("shop"))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void contactsUseVerifiedApplicationEmailAndAuthorityChangesAreNotCached(){
        var f=fixture(1346);policy.allowed.addAll(EnumSet.allOf(SubjectAccessPolicy.Content.class));
        var google=job(f.tenant(),()->tx.execute(status->sessions.google(authority.capture(f.client().loginHandle()),new GoogleIdTokenVerifier.Verified("https://accounts.google.com","provider-user","claim@example.test",true))));
        var googleRef=new SubjectReadRequest("shop",f.realm().id(),google.subjectId(),"notice");assertThat(job(f.tenant(),()->query.find(googleRef))).isPresent();assertThat(job(f.tenant(),()->contacts.verifiedEmail(googleRef))).isEmpty();
        var actor=register(f,"verified@example.test");var request=request(f,actor);assertThat(job(f.tenant(),()->contacts.verifiedEmail(request))).isPresent();
        job(f.tenant(),()->{var row=subjects.selectById(actor.subjectId());row.setEnabled(false);subjects.updateById(row);return null;});
        assertThat(job(f.tenant(),()->query.find(request)).orElseThrow().enabled()).isFalse();assertThat(job(f.tenant(),()->contacts.verifiedEmail(request))).isEmpty();
        doReturn(Optional.of(new TenantEntryQuery.SecurityState(f.tenant(),2,false,false))).when(tenants).securityState(f.tenant());
        assertThatThrownBy(()->job(f.tenant(),()->query.find(request))).isInstanceOf(AccessDeniedException.class);
    }
}
