package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
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
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest @RecordApplicationEvents
class IdentitySessionTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-p09-session-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));
        registry.add("sma.common.credentials.key-file",()->keyFile.toString());registry.add("sma.identity.sessions.maximum-per-subject",()->3);
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityAuthority authority;
    @Autowired IdentitySessionStore store;@Autowired IdentitySubjectMapper subjects;@Autowired IdentityExternalMapper externals;
    @Autowired IdentitySessionMapper sessionRows;@Autowired IdentityRefreshMapper refreshRows;
    @Autowired PlatformTransactionManager manager;@Autowired ApplicationEvents events;
    @MockitoSpyBean IdentityClock clock;@MockitoSpyBean TenantEntryQuery tenants;
    private final Instant now=Instant.parse("2026-11-11T00:00:00Z");
    @BeforeEach void time(){org.mockito.Mockito.doReturn(now).when(clock).now();org.mockito.Mockito.doAnswer(i->Optional.of(new TenantEntryQuery.SecurityState(i.getArgument(0),1,true,true)))
            .when(tenants).securityState(org.mockito.ArgumentMatchers.anyLong());}
    private <T>T admin(long tenant,Supplier<T> action){return Users.as(new SmaPrincipal(801,"identity-test",tenant,"admin",null,false,DataAccess.none(),
            Set.of("identity:realm:view","identity:realm:manage","identity:client:view","identity:client:manage"),"UTC"),action);}
    private <T>T job(long tenant,Supplier<T> action){return ActorContext.withinJob(tenant,"identity-login",action);}
    private record Fixture(long tenant,IdentityRealmService.View realm,IdentityClientService.View client,IdentityAuthority.Snapshot snapshot){}
    private Fixture fixture(long tenant,boolean web){
        var realm=admin(tenant,()->realms.create(new IdentityRealmService.Input("realm-"+UUID.randomUUID().toString().substring(0,8),"Realm",true,true,true)));
        var client=client(tenant,realm.id(),web);return new Fixture(tenant,realm,client,job(tenant,()->authority.capture(client.loginHandle())));
    }
    private IdentityClientService.View client(long tenant,long realm,boolean web){return admin(tenant,()->clients.create(new IdentityClientService.Input(realm,
            "client-"+UUID.randomUUID().toString().substring(0,8),"Client","shop",web?IdentityClientService.Platform.WEB:IdentityClientService.Platform.IOS,true,true,
            "123456-fixture.apps.googleusercontent.com",web?"test-web-google-secret".toCharArray():null,
            web?Set.of("https://app.example.test/cb"):Set.of("com.example.shop:/cb"),web?Set.of("https://app.example.test"):Set.of(),900,2592000)));}
    private GoogleIdTokenVerifier.Verified google(String subject){return new GoogleIdTokenVerifier.Verified("https://accounts.google.com",subject,"same-email@example.test",true);}
    private IdentitySessionStore.Issued issue(Fixture f,String subject){return job(f.tenant,()->store.google(f.snapshot,google(subject)));}
    private IdentitySessionStore.Access authenticate(Fixture f,IdentitySessionStore.Issued tokens){return job(f.tenant,()->store.authenticate(tokens.accessToken(),f.client.loginHandle(),tokens.mode()));}
    @Test void googleIdentityIsRealmScopedAndSameEmailDoesNotMergeSubjects(){
        var f=fixture(991,false);var one=issue(f,"GoogleSubject");var two=issue(f,"OtherSubject");var repeat=issue(f,"GoogleSubject");
        assertThat(one.subjectId()).isEqualTo(repeat.subjectId()).isNotEqualTo(two.subjectId());
        assertThat(authenticate(f,one).principal().audience()).isEqualTo("shop");
        assertThat(events.stream(ApplicationSubjectCreated.class).filter(e->e.tenantId()==991).count()).isEqualTo(2);
        job(991,()->{assertThat(subjects.selectCount(null)).isEqualTo(2);assertThat(externals.selectCount(null)).isEqualTo(2);return null;});
        var otherRealm=fixture(991,false);assertThat(issue(otherRealm,"GoogleSubject").subjectId()).isNotEqualTo(one.subjectId());
        var otherTenant=fixture(992,false);assertThat(issue(otherTenant,"GoogleSubject").subjectId()).isNotEqualTo(one.subjectId());
        assertThatThrownBy(()->job(992,()->store.authenticate(one.accessToken(),f.client.loginHandle(),"NATIVE"))).isInstanceOf(BizException.class);
        var otherClient=client(991,f.realm.id(),false);
        assertThatThrownBy(()->job(991,()->store.authenticate(one.accessToken(),otherClient.loginHandle(),"NATIVE"))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->admin(991,()->store.google(f.snapshot,google("not-a-login")))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void tokenMaterialIsHashedAndRollbackDoesNotLeaveAUsableIdentityOrSession(){
        var f=fixture(993,true);var issued=issue(f,"hashed");
        job(f.tenant,()->{
            var row=sessionRows.selectById(issued.sessionId());assertThat(row.getAccessHash()).hasSize(64).doesNotContain(issued.accessToken());
            var refresh=refreshRows.selectOne(new LambdaQueryWrapper<IdentityRefreshEntity>().eq(IdentityRefreshEntity::getSessionId,issued.sessionId()));
            assertThat(refresh.getTokenHash()).hasSize(64).doesNotContain(issued.refreshToken());assertThat(row.getCsrfHash()).isNotEqualTo(issued.csrfToken());return null;
        });
        assertThat(issued.toString()).doesNotContain(issued.accessToken(),issued.refreshToken(),issued.csrfToken());
        var aborted=job(f.tenant,()->new TransactionTemplate(manager).execute(tx->{var result=store.google(f.snapshot,google("aborted"));tx.setRollbackOnly();return result;}));
        assertThatThrownBy(()->authenticate(f,aborted)).isInstanceOf(BizException.class);
        job(f.tenant,()->{assertThat(subjects.selectCount(null)).isEqualTo(1);return null;});
    }
    @Test void refreshRotatesOnceAndReplayRevocationCommitsDespiteTheBusinessError(){
        var f=fixture(994,true);var first=issue(f,"refresh");
        assertThatThrownBy(()->job(f.tenant,()->store.refresh(first.refreshToken(),f.client.loginHandle(),"WEB","x".repeat(43)))).isInstanceOf(BizException.class);
        var second=job(f.tenant,()->store.refresh(first.refreshToken(),f.client.loginHandle(),"WEB",first.csrfToken()));
        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.refreshExpiresAt()).isEqualTo(first.refreshExpiresAt());assertThat(second.csrfToken()).isNotEqualTo(first.csrfToken());
        assertThatThrownBy(()->authenticate(f,first)).isInstanceOf(BizException.class);authenticate(f,second);
        assertThatThrownBy(()->job(f.tenant,()->new TransactionTemplate(manager).execute(tx->store.refresh(first.refreshToken(),f.client.loginHandle(),"WEB",first.csrfToken()))))
                .isInstanceOf(IdentitySessionStore.RefreshReplay.class);
        assertThatThrownBy(()->authenticate(f,second)).isInstanceOf(BizException.class);
        job(f.tenant,()->{assertThat(sessionRows.selectById(first.sessionId()).getRevocationReason()).isEqualTo("REFRESH_REUSE");return null;});
    }
    @Test void simultaneousRefreshHasOneSuccessAndReuseRevokesItsOnlySuccessor()throws Exception{
        var f=fixture(995,false);var first=issue(f,"race");var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Callable<Object> call=()->{start.await();try{return job(f.tenant,()->store.refresh(first.refreshToken(),f.client.loginHandle(),"NATIVE",null));}catch(IdentitySessionStore.RefreshReplay replay){return "REPLAY";}};
            var a=executor.submit(call);var b=executor.submit(call);start.countDown();var results=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertThat(results.stream().filter(IdentitySessionStore.Issued.class::isInstance)).hasSize(1);assertThat(results).contains("REPLAY");
            var successor=(IdentitySessionStore.Issued)results.stream().filter(IdentitySessionStore.Issued.class::isInstance).findFirst().orElseThrow();
            assertThatThrownBy(()->authenticate(f,successor)).isInstanceOf(BizException.class);
        }
        job(f.tenant,()->{assertThat(refreshRows.selectCount(null)).isEqualTo(2);return null;});
    }
    @Test void clientRealmTenantAndSubjectChangesCannotReviveOldSessionsOrSnapshots(){
        var f=fixture(996,false);var first=issue(f,"epochs");
        var disabled=admin(f.tenant,()->clients.disable(f.client.id(),1));assertThatThrownBy(()->authenticate(f,first)).isInstanceOf(BizException.class);
        var restored=admin(f.tenant,()->clients.update(f.client.id(),disabled.version(),new IdentityClientService.Input(f.realm.id(),f.client.code(),"Client","shop",IdentityClientService.Platform.IOS,
                true,true,f.client.googleClientId(),null,Set.of("com.example.shop:/cb"),Set.of(),900,2592000)));
        assertThatThrownBy(()->authenticate(f,first)).isInstanceOf(BizException.class);assertThatThrownBy(()->issue(f,"stale-login")).isInstanceOf(BizException.class);
        var current=new Fixture(f.tenant,f.realm,restored,job(f.tenant,()->authority.capture(restored.loginHandle())));var second=issue(current,"epochs");authenticate(current,second);
        job(f.tenant,()->{store.revokeSubject(second.subjectId());return null;});assertThatThrownBy(()->authenticate(current,second)).isInstanceOf(BizException.class);
        var third=issue(current,"epochs");
        org.mockito.Mockito.doReturn(Optional.of(new TenantEntryQuery.SecurityState(f.tenant,2,false,false))).when(tenants).securityState(f.tenant);
        assertThatThrownBy(()->authenticate(current,third)).isInstanceOf(BizException.class);
        org.mockito.Mockito.doReturn(Optional.of(new TenantEntryQuery.SecurityState(f.tenant,3,true,true))).when(tenants).securityState(f.tenant);
        assertThatThrownBy(()->authenticate(current,third)).isInstanceOf(BizException.class);
        var latest=new Fixture(f.tenant,f.realm,restored,job(f.tenant,()->authority.capture(restored.loginHandle())));var fourth=issue(latest,"epochs");
        admin(f.tenant,()->realms.disable(f.realm.id(),1));assertThatThrownBy(()->authenticate(latest,fourth)).isInstanceOf(BizException.class);
    }
    @Test void expiryModesRegistrationAndCapacityAreFailClosed(){
        var f=fixture(997,true);var first=issue(f,"capacity");issue(f,"capacity");issue(f,"capacity");
        assertThatThrownBy(()->issue(f,"capacity")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.SESSION_LIMIT));
        assertThatThrownBy(()->job(f.tenant,()->store.authenticate(first.accessToken(),f.client.loginHandle(),"NATIVE"))).isInstanceOf(BizException.class);
        org.mockito.Mockito.doReturn(now.plusSeconds(901)).when(clock).now();assertThatThrownBy(()->authenticate(f,first)).isInstanceOf(BizException.class);
        var fresh=job(f.tenant,()->store.refresh(first.refreshToken(),f.client.loginHandle(),"WEB",first.csrfToken()));authenticate(f,fresh);
        org.mockito.Mockito.doReturn(now.plusSeconds(2592001)).when(clock).now();assertThatThrownBy(()->job(f.tenant,()->store.refresh(fresh.refreshToken(),f.client.loginHandle(),"WEB",fresh.csrfToken()))).isInstanceOf(BizException.class);
        org.mockito.Mockito.doReturn(now).when(clock).now();var other=fixture(998,false);
        admin(other.tenant,()->realms.update(other.realm.id(),1,new IdentityRealmService.Input(other.realm.code(),"Realm",true,false,true)));
        var closed=new Fixture(other.tenant,other.realm,other.client,job(other.tenant,()->authority.capture(other.client.loginHandle())));
        assertThatThrownBy(()->issue(closed,"new-account")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.REGISTRATION_DISABLED));
    }
    @Test void concurrentIssuanceCannotExceedTheSubjectBudgetThroughAnOlderSnapshot()throws Exception{
        var f=fixture(999,false);issue(f,"shared");var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(3)){
            Callable<String> call=()->{start.await();try{issue(f,"shared");return "ISSUED";}catch(BizException rejected){
                if(rejected.getErrorCode()!=IdentityErrorCode.SESSION_LIMIT)throw rejected;return "LIMIT";}};
            var futures=List.of(executor.submit(call),executor.submit(call),executor.submit(call));start.countDown();
            var outcomes=new ArrayList<String>();for(var future:futures)outcomes.add(future.get(30,TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("ISSUED","ISSUED","LIMIT");
        }
        job(f.tenant,()->{assertThat(sessionRows.selectCount(null)).isEqualTo(3);assertThat(subjects.selectCount(null)).isEqualTo(1);return null;});
    }
    @Test void applicationLogoutRevokesItsOwnFamilyAndHumanIdentityCannotUseIt(){
        var f=fixture(1001,false);var issued=issue(f,"logout");var access=authenticate(f,issued);
        assertThatThrownBy(()->admin(f.tenant,()->{store.logout();return null;})).isInstanceOf(AccessDeniedException.class);
        var previous=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(access.principal(),null,List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try{store.logout();}finally{org.springframework.security.core.context.SecurityContextHolder.setContext(previous);}
        assertThatThrownBy(()->authenticate(f,issued)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->job(f.tenant,()->store.refresh(issued.refreshToken(),f.client.loginHandle(),"NATIVE",null))).isInstanceOf(BizException.class);
    }
}
