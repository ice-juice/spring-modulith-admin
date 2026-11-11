package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@IntegrationTest
class IdentityLoginTransactionTests {
    private static Path keyFile;private static RSAKey signingKey;
    @BeforeAll static void signing()throws Exception{signingKey=new RSAKeyGenerator(2048).keyID("login-integration").generate();}
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-p09-login-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
        registry.add("sma.identity.login.maximum-outstanding",()->3);registry.add("sma.identity.login.maximum-retained",()->10);
        registry.add("sma.identity.login.maximum-rate-keys",()->6);registry.add("sma.identity.login.tenant-per-minute",()->4);
        registry.add("sma.identity.login.tenant-per-day",()->8);registry.add("sma.identity.login.client-per-minute",()->3);registry.add("sma.identity.login.source-per-minute",()->2);
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;
    @MockitoSpyBean IdentityLoginStore store;@Autowired GoogleLoginCoordinator coordinator;
    @MockitoSpyBean IdentitySessionStore sessions;@MockitoSpyBean GoogleHttpTransport transport;
    @MockitoSpyBean IdentityClock clock;@MockitoSpyBean TenantEntryQuery tenants;
    @Autowired IdentityLoginTransactionMapper transactions;@Autowired IdentityLoginBudgetMapper budgets;@Autowired IdentityLoginRateMapper rates;
    @Autowired IdentitySubjectMapper subjects;@Autowired IdentitySessionMapper sessionRows;
    private final Instant now=Instant.parse("2026-11-11T00:00:00Z");private final String verifier="v".repeat(43);
    private final String callback="https://app.example.test/cb",origin="https://app.example.test",audience="123456-login.apps.googleusercontent.com";
    @BeforeEach void setup(){
        doReturn(now).when(clock).now();doAnswer(i->Optional.of(new TenantEntryQuery.SecurityState(i.getArgument(0),1,true,true))).when(tenants).securityState(anyLong());
        doReturn(new GoogleHttpTransport.JwksDocument(new JWKSet(signingKey.toPublicJWK()).toString(),Duration.ofHours(1))).when(transport).fetchKeys();
    }
    private <T>T admin(long tenant,Supplier<T> action){return Users.as(new SmaPrincipal(801,"login-test",tenant,"admin",null,false,DataAccess.none(),
            Set.of("identity:realm:manage","identity:realm:view","identity:client:manage","identity:client:view"),"UTC"),action);}
    private <T>T job(long tenant,Supplier<T> action){return ActorContext.withinJob(tenant,"identity-login",action);}
    private record Fixture(long tenant,IdentityRealmService.View realm,IdentityClientService.View client){}
    private Fixture fixture(long tenant,boolean web){var realm=admin(tenant,()->realms.create(new IdentityRealmService.Input("r-"+UUID.randomUUID().toString().substring(0,8),"Realm",true,true,true)));
        return new Fixture(tenant,realm,client(tenant,realm.id(),web));}
    private IdentityClientService.View client(long tenant,long realm,boolean web){return admin(tenant,()->clients.create(new IdentityClientService.Input(realm,
            "c-"+UUID.randomUUID().toString().substring(0,8),"Client","shop",web?IdentityClientService.Platform.WEB:IdentityClientService.Platform.IOS,true,true,
            audience,web?"protected-test-secret".toCharArray():null,Set.of(callback),web?Set.of(origin):Set.of(),900,2592000)));}
    private IdentityLoginStore.Start start(Fixture f,IdentityLoginStore.Flow flow,String ip){return new IdentityLoginStore.Start(f.client.loginHandle(),callback,
            f.client.platform()==IdentityClientService.Platform.WEB?origin:null,flow,GoogleProtocol.challenge(verifier),ip);}
    private IdentityLoginStore.Started begin(Fixture f,IdentityLoginStore.Flow flow,String ip){return job(f.tenant,()->coordinator.begin(start(f,flow,ip)));}
    private IdentityLoginStore.Completion completion(Fixture f,IdentityLoginStore.Started started,IdentityLoginStore.Flow flow,String credential){
        return new IdentityLoginStore.Completion(started.state(),started.proof(),verifier,f.client.loginHandle(),callback,
                f.client.platform()==IdentityClientService.Platform.WEB?origin:null,flow,credential);
    }
    private IdentityLoginTransactionEntity row(Fixture f){return job(f.tenant,()->transactions.selectOne(new LambdaQueryWrapper<IdentityLoginTransactionEntity>().orderByDesc(IdentityLoginTransactionEntity::getId).last("LIMIT 1")));}
    private String jwt(String nonce,String subject)throws Exception{
        var value=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),new JWTClaimsSet.Builder().issuer("https://accounts.google.com")
                .subject(subject).audience(audience).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600))).claim("nonce",nonce).build());
        value.sign(new RSASSASigner(signingKey));return value.serialize();
    }
    @Test void realSignedTokenConsumesPersistedProofAndCreatesAUsableScopedSession()throws Exception{
        var f=fixture(1011,false);var started=begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.1");var before=row(f);
        assertThat(before.getStateHash()).doesNotContain(started.state());assertThat(before.getProofHash()).doesNotContain(started.proof());
        assertThat(before.getNonceEnvelope()).doesNotContain(started.nonce());assertThat(started.authorizationUri()).isNull();
        var command=completion(f,started,IdentityLoginStore.Flow.ID_TOKEN,jwt(started.nonce(),"subject"));
        var issued=job(f.tenant,()->coordinator.complete(command));assertThat(issued.mode()).isEqualTo("NATIVE");
        job(f.tenant,()->{assertThat(sessions.authenticate(issued.accessToken(),f.client.loginHandle(),"NATIVE").principal().subjectId()).isEqualTo(issued.subjectId());
            assertThat(budgets.selectOne(null).getOutstanding()).isZero();assertThat(budgets.selectOne(null).getRetained()).isEqualTo(1);return null;});
        assertThat(row(f).getStatus()).isEqualTo("COMPLETED");assertThat(row(f).getNonceEnvelope()).isNull();
        assertThat(row(f).getSnapshotJson()).doesNotContain(issued.accessToken(),issued.refreshToken(),command.credential());
        assertThatThrownBy(()->job(f.tenant,()->coordinator.complete(command))).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.LOGIN_USED));
    }
    @Test void stateProofPkceClientCallbackOriginAndFlowCannotBeSwapped()throws Exception{
        var f=fixture(1012,true);var started=begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.2");String credential=jwt(started.nonce(),"web-subject");
        var c=completion(f,started,IdentityLoginStore.Flow.ID_TOKEN,credential);
        var invalid=List.of(new IdentityLoginStore.Completion(c.state(),"x".repeat(43),c.verifier(),c.loginHandle(),c.callbackUri(),c.origin(),c.flow(),credential),
                new IdentityLoginStore.Completion(c.state(),c.proof(),"x".repeat(43),c.loginHandle(),c.callbackUri(),c.origin(),c.flow(),credential),
                new IdentityLoginStore.Completion(c.state(),c.proof(),c.verifier(),"x".repeat(43),c.callbackUri(),c.origin(),c.flow(),credential),
                new IdentityLoginStore.Completion(c.state(),c.proof(),c.verifier(),c.loginHandle(),"https://evil.example/cb",c.origin(),c.flow(),credential),
                new IdentityLoginStore.Completion(c.state(),c.proof(),c.verifier(),c.loginHandle(),c.callbackUri(),"https://evil.example",c.flow(),credential),
                new IdentityLoginStore.Completion(c.state(),c.proof(),c.verifier(),c.loginHandle(),c.callbackUri(),c.origin(),IdentityLoginStore.Flow.CODE,credential));
        for(var input:invalid)assertThatThrownBy(()->job(f.tenant,()->coordinator.complete(input))).isInstanceOf(BizException.class);
        assertThat(row(f).getStatus()).isEqualTo("PENDING");
        assertThatThrownBy(()->job(1013,()->coordinator.complete(c))).isInstanceOf(BizException.class);
        var issued=job(f.tenant,()->coordinator.complete(c));assertThat(issued.mode()).isEqualTo("WEB");assertThat(issued.csrfToken()).hasSize(43);
    }
    @Test void codeExchangeRunsOutsideTheDatabaseTransactionAndOnlyOneConcurrentCompletionWins()throws Exception{
        var f=fixture(1014,true);var started=begin(f,IdentityLoginStore.Flow.CODE,"198.51.100.4");
        assertThat(started.authorizationUri().toString()).contains("code_challenge_method=S256");String credential=jwt(started.nonce(),"code-subject");
        doAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();assertThat(ActorContext.tenantId()).isEqualTo(f.tenant);
            assertThat((String)i.getArgument(0)).isEqualTo(audience);assertThat((String)i.getArgument(3)).isEqualTo(verifier);return credential;})
                .when(transport).exchange(anyString(),any(),anyString(),anyString(),anyString());
        var command=completion(f,started,IdentityLoginStore.Flow.CODE,"authorization-code");var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Callable<String> call=()->{gate.await();try{job(f.tenant,()->coordinator.complete(command));return "OK";}catch(BizException used){
                if(used.getErrorCode()!=IdentityErrorCode.LOGIN_USED)throw used;return "USED";}};
            var a=executor.submit(call);var b=executor.submit(call);gate.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","USED");
        }
        verify(transport,times(1)).exchange(anyString(),any(),anyString(),anyString(),anyString());
        job(f.tenant,()->{assertThat(sessionRows.selectCount(null)).isEqualTo(1);return null;});
    }
    @Test void badNonceAndFailedSessionCommitDoNotLeaveAccountsOrReusableTransactions()throws Exception{
        var f=fixture(1015,false);var started=begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.5");
        var wrong=completion(f,started,IdentityLoginStore.Flow.ID_TOKEN,jwt("x".repeat(43),"wrong-nonce"));
        assertThatThrownBy(()->job(f.tenant,()->coordinator.complete(wrong))).isInstanceOf(BizException.class);assertThat(row(f).getStatus()).isEqualTo("FAILED");
        var next=begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.5");var command=completion(f,next,IdentityLoginStore.Flow.ID_TOKEN,jwt(next.nonce(),"rolled-back"));
        doAnswer(i->{i.callRealMethod();throw new BizException(IdentityErrorCode.INVALID_SESSION);}).when(sessions).google(any(),any());
        assertThatThrownBy(()->job(f.tenant,()->coordinator.complete(command))).isInstanceOf(BizException.class);
        job(f.tenant,()->{assertThat(subjects.selectCount(null)).isZero();assertThat(sessionRows.selectCount(null)).isZero();assertThat(budgets.selectOne(null).getOutstanding()).isZero();return null;});
        assertThat(row(f).getStatus()).isEqualTo("FAILED");assertThat(row(f).getNonceEnvelope()).isNull();
    }
    @Test void expiryAndConfigurationChangesRejectPendingAndAlreadyVerifyingWork()throws Exception{
        var f=fixture(1016,false);var started=begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.6");
        var command=completion(f,started,IdentityLoginStore.Flow.ID_TOKEN,jwt(started.nonce(),"old"));
        admin(f.tenant,()->clients.disable(f.client.id(),1));
        assertThatThrownBy(()->job(f.tenant,()->coordinator.complete(command))).isInstanceOf(BizException.class);assertThat(row(f).getStatus()).isEqualTo("FAILED");
        var another=fixture(1017,false);var pending=begin(another,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.7");
        var input=completion(another,pending,IdentityLoginStore.Flow.ID_TOKEN,jwt(pending.nonce(),"expired"));
        var claim=job(another.tenant,()->store.claim(input)).prepared();assertThat(row(another).getStatus()).isEqualTo("VERIFYING");
        doReturn(now.plusSeconds(301)).when(clock).now();job(another.tenant,store::maintain);
        assertThat(row(another).getStatus()).isEqualTo("FAILED");assertThat(row(another).getNonceEnvelope()).isNull();
        assertThatThrownBy(()->job(another.tenant,()->store.finish(claim,new GoogleIdTokenVerifier.Verified("https://accounts.google.com","expired",null,false)))).isInstanceOf(BizException.class);
        doReturn(now.plusSeconds(86401)).when(clock).now();job(another.tenant,store::maintain);
        job(another.tenant,()->{assertThat(transactions.selectCount(null)).isZero();assertThat(budgets.selectOne(null).getRetained()).isZero();assertThat(budgets.selectOne(null).getOutstanding()).isZero();return null;});
    }
    @Test void crashedVerificationIsNeverReexchangedAndMaintenanceReleasesItsReservation()throws Exception{
        var f=fixture(1018,false);var started=begin(f,IdentityLoginStore.Flow.CODE,"198.51.100.8");
        var command=completion(f,started,IdentityLoginStore.Flow.CODE,"unknown-exchange-code");
        doThrow(new BizException(IdentityErrorCode.PROVIDER_UNAVAILABLE)).when(transport).exchange(anyString(),nullable(char[].class),anyString(),anyString(),anyString());
        doThrow(new IllegalStateException("database unavailable")).when(store).fail(any());
        assertThatThrownBy(()->job(f.tenant,()->coordinator.complete(command))).isInstanceOf(BizException.class);assertThat(row(f).getStatus()).isEqualTo("VERIFYING");
        assertThatThrownBy(()->job(f.tenant,()->coordinator.complete(command))).isInstanceOf(BizException.class);
        verify(transport,times(1)).exchange(anyString(),nullable(char[].class),anyString(),anyString(),anyString());
        doReturn(now.plusSeconds(301)).when(clock).now();job(f.tenant,store::maintain);
        assertThat(row(f).getStatus()).isEqualTo("FAILED");job(f.tenant,()->{assertThat(budgets.selectOne(null).getOutstanding()).isZero();return null;});
    }
    @Test void sourceRateAndOutstandingCapacityAreIsolatedAndDoNotLeakRawSourceAddresses(){
        var f=fixture(1019,false);begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.19");begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.19");
        assertThatThrownBy(()->begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.19")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.LOGIN_LIMIT));
        begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.20");
        assertThatThrownBy(()->begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.21")).isInstanceOf(BizException.class);
        job(f.tenant,()->{assertThat(transactions.selectCount(null)).isEqualTo(3);assertThat(budgets.selectOne(null).getOutstanding()).isEqualTo(3);
            assertThat(rates.selectList(null)).allSatisfy(r->assertThat(r.getScopeKey()).doesNotContain("198.51.100"));return null;});
        var other=fixture(1020,false);assertThat(begin(other,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.19").state()).hasSize(43);
        doReturn(now.plusSeconds(301)).when(clock).now();assertThat(begin(f,IdentityLoginStore.Flow.ID_TOKEN,"198.51.100.19").state()).hasSize(43);
        job(f.tenant,()->{assertThat(budgets.selectOne(null).getOutstanding()).isEqualTo(1);return null;});
    }
    @Test void unregisteredOriginsCallbacksAndNonLiteralSourceAddressesAreDeniedBeforePersistence(){
        var f=fixture(1021,true);var good=start(f,IdentityLoginStore.Flow.CODE,"198.51.100.21");
        for(var bad:List.of(new IdentityLoginStore.Start(good.loginHandle(),good.callbackUri(),"https://evil.example",good.flow(),good.pkceChallenge(),good.sourceAddress()),
                new IdentityLoginStore.Start(good.loginHandle(),"https://evil.example/cb",good.origin(),good.flow(),good.pkceChallenge(),good.sourceAddress()),
                new IdentityLoginStore.Start(good.loginHandle(),good.callbackUri(),good.origin(),good.flow(),good.pkceChallenge(),"metadata.google.internal"))){
            assertThatThrownBy(()->job(f.tenant,()->coordinator.begin(bad))).isInstanceOf(BizException.class);
        }
        job(f.tenant,()->{assertThat(transactions.selectCount(null)).isZero();assertThat(budgets.selectCount(null)).isZero();return null;});
    }
}
