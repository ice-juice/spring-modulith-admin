package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
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
import com.sma.admin.identity.internal.service.IdentityReauthenticationStore.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.*;

@IntegrationTest
class IdentityGoogleOperationTests {
    private static Path keyFile;private static RSAKey signingKey;
    @BeforeAll static void signing()throws Exception{signingKey=new RSAKeyGenerator(2048).keyID("google-operation").generate();}
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-google-operation-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
        registry.add("sma.identity.bindings.maximum-google-links",()->2);
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired GoogleLoginCoordinator login;
    @Autowired IdentityGoogleOperations operations;@Autowired IdentityLoginStore store;@Autowired IdentitySessionStore sessions;
    @Autowired IdentityReauthenticationStore grants;@Autowired IdentityExternalMapper external;@Autowired IdentityReauthenticationMapper grantRows;
    @Autowired IdentityLoginTransactionMapper transactions;@Autowired IdentitySubjectMapper subjects;@Autowired TransactionTemplate tx;
    @MockitoSpyBean IdentityExternalLinks links;@MockitoSpyBean GoogleHttpTransport transport;@MockitoSpyBean IdentityClock clock;
    private final Instant now=Instant.parse("2026-11-12T00:00:00Z");private final String verifier="v".repeat(43);
    private final String origin="https://app.example.test",callback=origin+"/cb",audience="123456-operations.apps.googleusercontent.com";
    private record Fixture(long tenant,IdentityRealmService.View realm,IdentityClientService.View client){}
    private record Account(IdentitySessionStore.Issued tokens,AppUserPrincipal actor){}
    @BeforeEach void setup(){
        doReturn(now).when(clock).now();doReturn(new GoogleHttpTransport.JwksDocument(new JWKSet(signingKey.toPublicJWK()).toString(),Duration.ofHours(1))).when(transport).fetchKeys();
    }
    private <T>T admin(long tenant,Supplier<T> work){return Users.as(new SmaPrincipal(802,"operation-test",tenant,"admin",null,false,DataAccess.none(),
            Set.of("identity:realm:manage","identity:client:manage"),"UTC"),work);}
    private <T>T job(long tenant,Supplier<T> work){return ActorContext.withinJob(tenant,"google-operation-test",work);}
    private Fixture fixture(long tenant){return admin(tenant,()->{
        var realm=realms.create(new IdentityRealmService.Input("google","Google",true,true,true));
        var client=clients.create(new IdentityClientService.Input(realm.id(),"web","Web","shop",IdentityClientService.Platform.WEB,true,true,
                audience,"test-secret-value".toCharArray(),Set.of(callback),Set.of(origin),900,2592000));return new Fixture(tenant,realm,client);
    });}
    private IdentityLoginStore.Start start(Fixture f){return new IdentityLoginStore.Start(f.client.loginHandle(),callback,origin,IdentityLoginStore.Flow.CODE,GoogleProtocol.challenge(verifier),"198.51.100.1");}
    private IdentityLoginStore.Completion completion(Fixture f,IdentityLoginStore.Started started,String sub)throws Exception{
        String jwt=jwt(started.nonce(),sub);
        doAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return jwt;})
                .when(transport).exchange(anyString(),any(),anyString(),anyString(),anyString());
        return new IdentityLoginStore.Completion(started.state(),started.proof(),verifier,f.client.loginHandle(),callback,origin,IdentityLoginStore.Flow.CODE,"provider-code");
    }
    private String jwt(String nonce,String sub)throws Exception{
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com").subject(sub).audience(audience).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("nonce",nonce).claim("email","same@example.test").claim("email_verified",true).build());jwt.sign(new RSASSASigner(signingKey));return jwt.serialize();
    }
    private Account login(Fixture f,String sub)throws Exception{
        var started=job(f.tenant,()->login.begin(start(f)));var command=completion(f,started,sub);var tokens=job(f.tenant,()->login.complete(command));
        return new Account(tokens,job(f.tenant,()->sessions.authenticate(tokens.accessToken(),f.client.loginHandle(),"WEB").principal()));
    }
    private IdentityLoginStore.Started reauthStart(Fixture f,AppUserPrincipal actor){return job(f.tenant,()->operations.beginReauthentication(start(f),actor,Action.GOOGLE_BINDING,"google"));}
    private Issued reauth(Fixture f,Account account,String sub)throws Exception{var begun=reauthStart(f,account.actor);var input=completion(f,begun,sub);return job(f.tenant,()->operations.completeReauthentication(input,account.actor));}
    private Use use(Issued grant){return new Use(grant.grant(),Action.GOOGLE_BINDING,"google");}
    private IdentityLoginTransactionEntity latest(Fixture f){return job(f.tenant,()->transactions.selectOne(new LambdaQueryWrapper<IdentityLoginTransactionEntity>().orderByDesc(IdentityLoginTransactionEntity::getId).last("LIMIT 1")));}
    private void denied(Runnable action,IdentityErrorCode code){assertThatThrownBy(action::run).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(code));}

    @Test void loginReauthenticationAndLinkTransactionsCannotBeInterchanged()throws Exception{
        var f=fixture(1241);var account=login(f,"owner");var begun=reauthStart(f,account.actor);assertThat(begun.authorizationUri().toString()).contains("prompt=select_account");
        var command=completion(f,begun,"owner");
        denied(()->job(f.tenant,()->login.complete(command)),IdentityErrorCode.INVALID_GOOGLE_PROOF);
        denied(()->job(f.tenant,()->operations.completeLink(command,account.actor)),IdentityErrorCode.INVALID_GOOGLE_PROOF);
        assertThat(latest(f).getStatus()).isEqualTo("PENDING");
        var grant=job(f.tenant,()->operations.completeReauthentication(command,account.actor));assertThat(grant.grant()).hasSize(43);
        denied(()->job(f.tenant,()->operations.completeReauthentication(command,account.actor)),IdentityErrorCode.LOGIN_USED);
        var alreadyLinked=job(f.tenant,()->operations.beginLink(start(f),account.actor,use(grant)));var existingInput=completion(f,alreadyLinked,"owner");
        var unchanged=job(f.tenant,()->operations.completeLink(existingInput,account.actor));assertThat(unchanged.changed()).isFalse();assertThat(unchanged.signInRequired()).isFalse();
        assertThat(job(f.tenant,()->sessions.authenticate(account.tokens.accessToken(),f.client.loginHandle(),"WEB").principal())).isEqualTo(account.actor);
        var normal=job(f.tenant,()->login.begin(start(f)));assertThat(normal.authorizationUri().toString()).doesNotContain("prompt=");var normalInput=completion(f,normal,"owner");
        denied(()->job(f.tenant,()->operations.completeReauthentication(normalInput,account.actor)),IdentityErrorCode.INVALID_GOOGLE_PROOF);
        assertThat(job(f.tenant,()->login.complete(normalInput)).subjectId()).isEqualTo(account.actor.subjectId());
    }
    @Test void sameEmailDifferentGoogleSubjectCannotProveOriginalIdentity()throws Exception{
        var f=fixture(1242);var account=login(f,"owner");var begun=reauthStart(f,account.actor);var input=completion(f,begun,"different-sub");
        denied(()->job(f.tenant,()->operations.completeReauthentication(input,account.actor)),IdentityErrorCode.INVALID_REAUTHENTICATION);
        assertThat(latest(f).getStatus()).isEqualTo("FAILED");assertThat(job(f.tenant,()->grantRows.selectCount(null))).isZero();
        assertThat(job(f.tenant,()->subjects.selectCount(null))).isEqualTo(1);
    }
    @Test void linkConsumesApprovalOnceAndRevokesOldSessionsWithoutCreatingAnotherSubject()throws Exception{
        var f=fixture(1243);var account=login(f,"owner");var grant=reauth(f,account,"owner");
        var begun=job(f.tenant,()->operations.beginLink(start(f),account.actor,use(grant)));var input=completion(f,begun,"second");
        denied(()->job(f.tenant,()->operations.beginLink(start(f),account.actor,use(grant))),IdentityErrorCode.INVALID_REAUTHENTICATION);
        denied(()->job(f.tenant,()->login.complete(input)),IdentityErrorCode.INVALID_GOOGLE_PROOF);
        var linked=job(f.tenant,()->operations.completeLink(input,account.actor));assertThat(linked.changed()).isTrue();assertThat(linked.signInRequired()).isTrue();
        denied(()->job(f.tenant,()->sessions.authenticate(account.tokens.accessToken(),f.client.loginHandle(),"WEB")),IdentityErrorCode.INVALID_SESSION);
        assertThat(job(f.tenant,()->subjects.selectCount(null))).isEqualTo(1);assertThat(login(f,"second").actor.subjectId()).isEqualTo(account.actor.subjectId());
        assertThat(latest(f).getSnapshotJson()).doesNotContain(grant.grant());
    }
    @Test void failedCreationRollsBackApprovalAndFailedFinishRollsBackLinkAndSessionRevocation()throws Exception{
        var f=fixture(1244);var account=login(f,"owner");var grant=reauth(f,account,"owner");var valid=start(f);
        var bad=new IdentityLoginStore.Start(valid.loginHandle(),"https://evil.example/cb",origin,valid.flow(),valid.pkceChallenge(),valid.sourceAddress());
        assertThatThrownBy(()->job(f.tenant,()->operations.beginLink(bad,account.actor,use(grant)))).isInstanceOf(BizException.class);
        var begun=job(f.tenant,()->operations.beginLink(valid,account.actor,use(grant)));var command=completion(f,begun,"second");
        IdentityExternalLinks target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(links);
        doAnswer(i->{i.callRealMethod();throw new IllegalStateException("rollback");}).when(target).add(any(),any(),any());
        assertThatThrownBy(()->job(f.tenant,()->operations.completeLink(command,account.actor))).isInstanceOf(IllegalStateException.class);
        assertThat(latest(f).getStatus()).isEqualTo("FAILED");assertThat(job(f.tenant,()->external.selectCount(null))).isEqualTo(1);
        assertThat(job(f.tenant,()->sessions.authenticate(account.tokens.accessToken(),f.client.loginHandle(),"WEB").principal())).isEqualTo(account.actor);
        denied(()->job(f.tenant,()->operations.completeLink(command,account.actor)),IdentityErrorCode.LOGIN_USED);
    }
    @Test void activeAndHistoricalLinksOfAnotherSubjectNeverMoveOrMerge()throws Exception{
        var f=fixture(1245);var owner=login(f,"owner");var other=login(f,"other");
        for(boolean historical:List.of(false,true)){
            if(historical)job(f.tenant,()->tx.execute(status->external.delete(new LambdaQueryWrapper<IdentityExternalEntity>().eq(IdentityExternalEntity::getSubjectId,other.actor.subjectId()))));
            var grant=reauth(f,owner,"owner");var begun=job(f.tenant,()->operations.beginLink(start(f),owner.actor,use(grant)));var command=completion(f,begun,"other");
            denied(()->job(f.tenant,()->operations.completeLink(command,owner.actor)),IdentityErrorCode.IDENTITY_LINK_CONFLICT);
            assertThat(job(f.tenant,()->subjects.selectCount(null))).isEqualTo(2);
        }
        var anonymous=job(f.tenant,()->login.begin(start(f)));var input=completion(f,anonymous,"other");
        denied(()->job(f.tenant,()->login.complete(input)),IdentityErrorCode.REVOKED_IDENTITY_LINK);
    }
    @Test void onlyExplicitSameOwnerBindingRestoresHistoryAndActiveLimitIsEnforced()throws Exception{
        var f=fixture(1246);var account=login(f,"owner");var grant=reauth(f,account,"owner");
        var begun=job(f.tenant,()->operations.beginLink(start(f),account.actor,use(grant)));var input=completion(f,begun,"second");var link=job(f.tenant,()->operations.completeLink(input,account.actor));
        var renewed=login(f,"owner");job(f.tenant,()->external.deleteById(link.linkId()));
        var approval=reauth(f,renewed,"owner");var restore=job(f.tenant,()->operations.beginLink(start(f),renewed.actor,use(approval)));var restoreInput=completion(f,restore,"second");
        assertThat(job(f.tenant,()->operations.completeLink(restoreInput,renewed.actor)).linkId()).isEqualTo(link.linkId());
        var active=login(f,"owner");var capacityGrant=reauth(f,active,"owner");var capacity=job(f.tenant,()->operations.beginLink(start(f),active.actor,use(capacityGrant)));var excess=completion(f,capacity,"third");
        denied(()->job(f.tenant,()->operations.completeLink(excess,active.actor)),IdentityErrorCode.IDENTITY_LINK_LIMIT);
        assertThat(job(f.tenant,()->external.selectCount(null))).isEqualTo(2);
    }
    @Test void originalSessionTenantAndConfigurationAreRecheckedBeforeAndAfterProviderWork()throws Exception{
        var f=fixture(1247);var account=login(f,"owner");var otherSession=login(f,"owner");var begun=reauthStart(f,account.actor);var input=completion(f,begun,"owner");
        assertThatThrownBy(()->job(1248,()->operations.completeReauthentication(input,account.actor))).isInstanceOf(BizException.class);
        assertThat(latest(f).getStatus()).isEqualTo("PENDING");
        denied(()->job(f.tenant,()->operations.completeReauthentication(input,otherSession.actor)),IdentityErrorCode.INVALID_REAUTHENTICATION);
        assertThat(latest(f).getStatus()).isEqualTo("FAILED");
        var pending=reauthStart(f,account.actor);var command=completion(f,pending,"owner");String jwt=jwt(pending.nonce(),"owner");
        doAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();job(f.tenant,()->{sessions.revokeSubject(account.actor.subjectId());return null;});return jwt;})
                .when(transport).exchange(anyString(),any(),anyString(),anyString(),anyString());
        assertThatThrownBy(()->job(f.tenant,()->operations.completeReauthentication(command,account.actor))).isInstanceOf(BizException.class);
        assertThat(latest(f).getStatus()).isEqualTo("FAILED");
        var fresh=login(f,"owner");var configuration=reauthStart(f,fresh.actor);var changed=completion(f,configuration,"owner");admin(f.tenant,()->clients.disable(f.client.id(),f.client.version()));
        denied(()->job(f.tenant,()->operations.completeReauthentication(changed,fresh.actor)),IdentityErrorCode.INVALID_SESSION);
    }
    @Test void outerTransactionsAreRejectedBeforeClaimOrProviderCall()throws Exception{
        var f=fixture(1249);var account=login(f,"owner");var begun=reauthStart(f,account.actor);var command=completion(f,begun,"owner");
        clearInvocations(transport);
        assertThatThrownBy(()->job(f.tenant,()->tx.execute(status->operations.completeReauthentication(command,account.actor)))).isInstanceOf(IllegalStateException.class);
        verify(transport,never()).exchange(anyString(),any(),anyString(),anyString(),anyString());assertThat(latest(f).getStatus()).isEqualTo("PENDING");
    }
}
