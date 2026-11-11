package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.identity.internal.service.IdentityEmailChallengeStore.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/** State-only tests: synthetic delivery IDs deliberately do not claim outbox/SMTP acceptance. */
@IntegrationTest
class IdentityEmailChallengeTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);keyFile=Files.createTempFile("sma-email-challenges-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(bytes));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
        registry.add("sma.identity.email.maximum-outstanding",()->4);registry.add("sma.identity.email.maximum-retained",()->8);registry.add("sma.identity.email.target-per-day",()->2);
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityEmailConfigurationService configurations;
    @Autowired IdentityEmailConfigurationMapper configurationRows;@Autowired IdentityEmailChallengeStore store;@Autowired IdentityEmailChallengeMapper rows;
    @Autowired IdentityEmailBudgetMapper budgets;@Autowired IdentitySubjectMapper subjects;@Autowired TransactionTemplate tx;
    @MockitoSpyBean IdentityClock clock;
    private Instant now;private final String origin="https://app.example.test";
    @BeforeEach void time(){now=Instant.parse("2026-11-11T00:00:00Z");doAnswer(i->now).when(clock).now();}
    private <T>T job(long tenant,Supplier<T> action){return ActorContext.withinJob(tenant,"identity-email-test",action);}
    private <T>T admin(long tenant,Supplier<T> action){return Users.as(new SmaPrincipal(131,"email-test",tenant,"admin",null,false,DataAccess.none(),
            Set.of("identity:realm:manage","identity:client:manage","identity:client:view"),"UTC"),action);}
    private record Fixture(long tenant,IdentityRealmService.View realm,IdentityClientService.View client){}
    private Fixture fixture(long tenant){return admin(tenant,()->{
        String code="email-"+UUID.randomUUID().toString().substring(0,8);var realm=realms.create(new IdentityRealmService.Input(code,"Email",true,true,true));
        var client=clients.create(new IdentityClientService.Input(realm.id(),code,"Email","shop",IdentityClientService.Platform.WEB,true,false,null,null,
                Set.of(origin+"/cb"),Set.of(origin),900,2592000));
        var configuration=new IdentityEmailConfigurationEntity();configuration.setClientId(client.id());configuration.setLanguage("en-US");configuration.setVersion(1L);
        configuration.setEnabled(true);configuration.setRegistrationTemplate("registration");configuration.setResetTemplate("password-reset");configuration.setBindingTemplate("email-binding");configurationRows.insert(configuration);
        return new Fixture(tenant,realm,client);
    });}
    private record Issued(long id,String handle,String proof,String code,Fixture fixture,Purpose purpose){}
    private Start start(Fixture f,String address,Purpose purpose,Long subject,Long generation){return new Start(f.client.loginHandle(),"en-US",purpose,address,subject,generation,origin,"127.0.0.1");}
    private Issued create(Fixture f,String address,Purpose purpose,Long subject,Long generation,boolean attached){return job(f.tenant,()->tx.execute(status->{
        try(var prepared=store.begin(start(f,address,purpose,subject,generation))){
            if(attached)store.attachDelivery(prepared.challengeId(),9001);char[] code=prepared.code().copy();
            try{return new Issued(prepared.challengeId(),prepared.handle(),prepared.proof(),new String(code),f,purpose);}finally{Arrays.fill(code,'\0');}
        }
    }));}
    private Issued create(Fixture f,String address){return create(f,address,Purpose.REGISTRATION,null,null,true);}
    private Result verify(Issued issued,String code){return job(issued.fixture.tenant,()->store.verify(command(issued,code,issued.proof,issued.fixture.client.loginHandle(),issued.purpose,origin)));}
    private Verification command(Issued i,String code,String proof,String client,Purpose purpose,String from){return new Verification(i.handle,proof,code,client,purpose,from,"127.0.0.1");}
    private Use use(Issued i,String grant){return new Use(grant,i.proof,i.fixture.client.loginHandle(),i.purpose,origin);}
    private IdentityEmailChallengeEntity row(Issued i){return job(i.fixture.tenant,()->rows.selectById(i.id));}
    private IdentityEmailBudgetEntity budget(long tenant){return job(tenant,()->budgets.selectOne(new LambdaQueryWrapper<IdentityEmailBudgetEntity>()));}
    private String wrong(Issued i){return i.code.equals("000000")?"111111":"000000";}

    @Test void creationMustShareTransactionWithDeliveryAndPreparedAloneCannotVerify(){
        var f=fixture(1141);
        assertThatThrownBy(()->job(f.tenant,()->tx.execute(status->{try(var prepared=store.begin(start(f,"user@example.test",Purpose.REGISTRATION,null,null))){throw new IllegalStateException("rollback");}})))
                .isInstanceOf(IllegalStateException.class);
        assertThat(job(f.tenant,()->rows.selectCount(null))).isZero();assertThat(budget(f.tenant)).isNull();
        var unbound=create(f,"user@example.test",Purpose.REGISTRATION,null,null,false);
        assertThat(verify(unbound,unbound.code).grant()).isNull();assertThat(row(unbound).getStatus()).isEqualTo("PREPARED");
        var pending=create(f,"other@example.test");var stored=row(pending);
        assertThat(stored.getTargetEnvelope()).startsWith("v1.test.").doesNotContain("other@example.test");
        assertThat(stored.getCodeFingerprint()).startsWith("h1.test.");assertThat(stored.getProofHash()).isNotEqualTo(pending.proof);
        assertThat(stored.getHandleHash()).isNotEqualTo(pending.handle);assertThat(stored.getDeliveryId()).isEqualTo(9001);
    }
    @Test void wrongCodeAttemptsCommitIndependentlyAndFifthFailureErasesSecrets(){
        var issued=create(fixture(1142),"guess@example.test");
        assertThatThrownBy(()->job(1142,()->tx.execute(status->{assertThat(store.verify(command(issued,wrong(issued),issued.proof,issued.fixture.client.loginHandle(),issued.purpose,origin)).error())
                .isEqualTo(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);throw new IllegalStateException("outer rollback");}))).isInstanceOf(IllegalStateException.class);
        assertThat(row(issued).getFailures()).isEqualTo(1);
        for(int attempt=1;attempt<5;attempt++)assertThat(verify(issued,wrong(issued)).error()).isEqualTo(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);
        var failed=row(issued);assertThat(failed.getStatus()).isEqualTo("FAILED");assertThat(failed.getTargetEnvelope()).isNull();assertThat(failed.getCodeFingerprint()).isNull();
        assertThat(verify(issued,issued.code).grant()).isNull();assertThat(budget(1142).getOutstanding()).isZero();assertThat(budget(1142).getMinuteGuesses()).isEqualTo(6);
    }
    @Test void correctCodeIssuesOnlyOneGrantAndDomainMutationRollbackPreservesIt(){
        var f=fixture(1143);var issued=create(f,"owner@example.test");var verified=verify(issued,issued.code);assertThat(verified.error()).isNull();assertThat(verified.grant()).hasSize(43);
        assertThat(row(issued).getCodeFingerprint()).isNull();assertThat(row(issued).getGrantHash()).isNotEqualTo(verified.grant());
        assertThat(verify(issued,issued.code).error()).isEqualTo(IdentityErrorCode.EMAIL_CHALLENGE_USED);
        assertThatThrownBy(()->job(f.tenant,()->tx.execute(status->store.consume(use(issued,verified.grant()),grant->{
            var subject=new IdentitySubjectEntity();subject.setRealmId(f.realm.id());subject.setEnabled(true);subject.setSecurityVersion(1L);subjects.insert(subject);throw new IllegalStateException("domain rollback");
        })))).isInstanceOf(IllegalStateException.class);
        assertThat(row(issued).getStatus()).isEqualTo("VERIFIED");assertThat(job(f.tenant,()->subjects.selectCount(null))).isZero();
        String address=job(f.tenant,()->tx.execute(status->store.consume(use(issued,verified.grant()),Granted::address)));assertThat(address).isEqualTo("owner@example.test");
        assertThat(row(issued).getStatus()).isEqualTo("CONSUMED");assertThat(row(issued).getGrantHash()).isNull();assertThat(row(issued).getTargetEnvelope()).isNull();
        assertThatThrownBy(()->job(f.tenant,()->tx.execute(status->store.consume(use(issued,verified.grant()),Granted::address)))).isInstanceOf(BizException.class);
    }
    @Test void proofPurposeOriginClientAndTenantCannotBeExchangedAndWrongContextDoesNotConsume(){
        var f=fixture(1144);var other=fixture(1144);var foreign=fixture(1145);var issued=create(f,"scoped@example.test");
        for(var command:List.of(command(issued,issued.code,"x".repeat(43),f.client.loginHandle(),issued.purpose,origin),
                command(issued,issued.code,issued.proof,f.client.loginHandle(),Purpose.PASSWORD_RESET,origin),
                command(issued,issued.code,issued.proof,f.client.loginHandle(),issued.purpose,"https://evil.example.test"),
                command(issued,issued.code,issued.proof,other.client.loginHandle(),issued.purpose,origin)))
            assertThat(job(f.tenant,()->store.verify(command)).grant()).isNull();
        assertThat(job(foreign.tenant,()->store.verify(command(issued,issued.code,issued.proof,foreign.client.loginHandle(),issued.purpose,origin))).grant()).isNull();
        assertThat(row(issued).getFailures()).isZero();var verified=verify(issued,issued.code);assertThat(verified.error()).isNull();
        assertThatThrownBy(()->job(f.tenant,()->tx.execute(status->store.consume(new Use(verified.grant(),issued.proof,f.client.loginHandle(),Purpose.PASSWORD_RESET,origin),Granted::address))))
                .isInstanceOf(BizException.class);
        String address=job(f.tenant,()->tx.execute(status->store.consume(use(issued,verified.grant()),Granted::address)));assertThat(address).isEqualTo("scoped@example.test");
    }
    @Test void configurationAndSubjectGenerationChangesPermanentlyInvalidatePendingProof(){
        var f=fixture(1146);var issued=create(f,"configuration@example.test");
        admin(f.tenant,()->configurations.put(f.client.id(),"en-US",1,new IdentityEmailConfigurationService.Input(false,"registration","password-reset","email-binding")));
        assertThat(verify(issued,issued.code).error()).isEqualTo(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);assertThat(row(issued).getStatus()).isEqualTo("FAILED");
        var second=fixture(1147);var subject=job(second.tenant,()->{var value=new IdentitySubjectEntity();value.setRealmId(second.realm.id());value.setEnabled(true);value.setSecurityVersion(1L);subjects.insert(value);return value;});
        var binding=create(second,"binding@example.test",Purpose.EMAIL_BINDING,subject.getId(),1L,true);
        job(second.tenant,()->{subject.setSecurityVersion(2L);subjects.updateById(subject);return null;});
        assertThat(verify(binding,binding.code).error()).isEqualTo(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);assertThat(row(binding).getStatus()).isEqualTo("FAILED");
        var third=fixture(1148);var realmProof=create(third,"realm@example.test");admin(third.tenant,()->realms.disable(third.realm.id(),1));
        assertThat(verify(realmProof,realmProof.code).error()).isEqualTo(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);assertThat(row(realmProof).getStatus()).isEqualTo("FAILED");
    }
    @Test void cooldownDailyTargetAndCapacityBudgetsAreBoundedAndMaintenanceErasesExpiredData(){
        var f=fixture(1149);var first=create(f,"quota@example.test");
        assertThatThrownBy(()->create(f,"quota@example.test")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.EMAIL_CHALLENGE_LIMIT));
        now=now.plusSeconds(61);create(f,"quota@example.test");now=now.plusSeconds(61);
        assertThatThrownBy(()->create(f,"quota@example.test")).isInstanceOf(BizException.class);
        create(f,"second@example.test");create(f,"third@example.test");assertThat(budget(f.tenant).getOutstanding()).isEqualTo(4);
        assertThatThrownBy(()->create(f,"fourth@example.test")).isInstanceOf(BizException.class);
        now=now.plusSeconds(601);assertThat(job(f.tenant,store::maintain)).isGreaterThanOrEqualTo(4);
        assertThat(row(first).getTargetEnvelope()).isNull();assertThat(budget(f.tenant).getOutstanding()).isZero();
        now=now.plus(Duration.ofDays(2));job(f.tenant,store::maintain);assertThat(budget(f.tenant).getRetained()).isZero();assertThat(job(f.tenant,()->rows.selectCount(null))).isZero();
        assertThat(job(1150,store::maintain)).isZero();assertThat(budget(1150)).isNull();
    }
    @Test void concurrentVerificationAndGrantUseEachHaveExactlyOneWinner()throws Exception{
        var f=fixture(1151);var issued=create(f,"race@example.test");var pool=Executors.newFixedThreadPool(2);
        try{
            var start=new CountDownLatch(1);var results=new ArrayList<Future<Result>>();for(int i=0;i<2;i++)results.add(pool.submit(()->{start.await();return verify(issued,issued.code);}));start.countDown();
            List<Result> values=new ArrayList<>();for(var future:results)values.add(future.get(30,TimeUnit.SECONDS));assertThat(values.stream().filter(value->value.error()==null)).hasSize(1);
            String grant=values.stream().filter(value->value.error()==null).findFirst().orElseThrow().grant();var mutationCount=new AtomicInteger();var consumeStart=new CountDownLatch(1);var outcomes=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++)outcomes.add(pool.submit(()->{consumeStart.await();try{return job(f.tenant,()->tx.execute(status->store.consume(use(issued,grant),value->{mutationCount.incrementAndGet();return true;})));}catch(BizException rejected){return false;}}));
            consumeStart.countDown();int winners=0;for(var outcome:outcomes)if(outcome.get(30,TimeUnit.SECONDS))winners++;assertThat(winners).isEqualTo(1);assertThat(mutationCount.get()).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void grantExpiresWithoutExtendingChallengeAndWrongGuessesHaveSourceBudget(){
        var f=fixture(1152);var issued=create(f,"expire@example.test");var verified=verify(issued,issued.code);now=now.plusSeconds(121);
        assertThatThrownBy(()->job(f.tenant,()->tx.execute(status->store.consume(use(issued,verified.grant()),Granted::address)))).isInstanceOf(BizException.class);
        job(f.tenant,store::maintain);assertThat(row(issued).getStatus()).isEqualTo("EXPIRED");assertThat(row(issued).getGrantHash()).isNull();
        for(int i=0;i<31;i++){
            var missing=new Verification("z".repeat(43),"y".repeat(43),"123456",f.client.loginHandle(),Purpose.REGISTRATION,origin,"10.0.0.2");
            var result=job(f.tenant,()->store.verify(missing));assertThat(result.error()).isEqualTo(i<30?IdentityErrorCode.INVALID_EMAIL_CHALLENGE:IdentityErrorCode.EMAIL_CHALLENGE_LIMIT);
        }
    }
}
