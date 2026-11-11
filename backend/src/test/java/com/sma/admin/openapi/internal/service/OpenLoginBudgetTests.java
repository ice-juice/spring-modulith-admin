package com.sma.admin.openapi.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.openapi.OpenapiErrorCode;
import com.sma.admin.openapi.internal.persistence.*;
import com.sma.admin.support.*;

@IntegrationTest
class OpenLoginBudgetTests {
    private static final String APP="a".repeat(32);
    @Autowired LoginBudgetMapper rows;
    @Autowired LoginBudgetGuardMapper guards;
    @Autowired SecretCipher secrets;
    @Autowired OpenapiProperties properties;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void prepare(){
        // The integration harness has already validated/cleaned its dedicated test schema.
        jdbc.update("DELETE FROM openapi_login_budget");
        secrets.encrypt("test-key-load","p08:budget-test");
    }
    @AfterEach void clearOnlyTheTestCounters(){jdbc.update("DELETE FROM openapi_login_budget");}
    private OpenLoginThrottle node(){return new OpenLoginThrottle(rows,guards,new SecretCipher(properties),transactions);}
    private void rateLimited(Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(BizException.class,error->assertThat(error.getErrorCode()).isEqualTo(OpenapiErrorCode.RATE_LIMIT));}
    private LoginBudgetEntity counter(String scope){return rows.selectList(null).stream().filter(row->row.getScope().equals(scope)).findFirst().orElseThrow();}

    @Test void independentNodesAndTenantsShareOneBudgetAndStoreOnlyKeyedSubjects(){
        var a=node();var b=node();String source="192.0.2.1";
        for(int i=0;i<10;i++){var service=i%2==0?a:b;long tenant=98321+i%2;Users.as(Users.principal(101,tenant,DataAccess.own()),()->{service.check(source,APP);return null;});}
        rateLimited(()->b.check(source,APP));
        assertThat(counter("PAIR").getAttemptCount()).isEqualTo(10);assertThat(counter("SOURCE").getAttemptCount()).isEqualTo(11);
        assertThat(rows.selectList(null)).hasSize(2).allSatisfy(row->assertThat(row.getSubjectHash()).matches("[a-f0-9]{64}").doesNotContain(source,APP));
        var key=guards.selectById(1L);assertThat(key.getKeyCipher()).isNotBlank();String id=key.getKeyId();
        rateLimited(()->node().check(source,APP));assertThat(guards.selectById(1L).getKeyId()).isEqualTo(id);
    }
    @Test void concurrentNodesReserveExactlyTheSharedPairLimit() throws Exception {
        var a=node();var b=node();var start=new CountDownLatch(1);var accepted=new AtomicInteger();var denied=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(8)){
            var futures=new ArrayList<Future<?>>();
            for(int i=0;i<32;i++){var service=i%2==0?a:b;futures.add(pool.submit(()->{
                try{start.await();service.check("192.0.2.2",APP);accepted.incrementAndGet();}
                catch(BizException limited){assertThat(limited.getErrorCode()).isEqualTo(OpenapiErrorCode.RATE_LIMIT);denied.incrementAndGet();}
                catch(InterruptedException interrupted){throw new IllegalStateException(interrupted);}
            }));}
            start.countDown();for(var future:futures)future.get(45,TimeUnit.SECONDS);
        }
        assertThat(accepted).hasValue(10);assertThat(denied).hasValue(22);assertThat(counter("SOURCE").getAttemptCount()).isEqualTo(32);
    }
    @Test void outerRollbackCannotRefundAndChangingUnknownAppIdsCannotBypassTheSourceLimit(){
        var a=node();new TransactionTemplate(transactions).execute(status->{a.check("192.0.2.3",APP);status.setRollbackOnly();return null;});
        assertThat(counter("SOURCE").getAttemptCount()).isEqualTo(1);
        for(int i=1;i<60;i++)a.check("192.0.2.3",String.format("%032x",i));
        rateLimited(()->node().check("192.0.2.3","b".repeat(32)));assertThat(counter("SOURCE").getAttemptCount()).isEqualTo(60);
    }
    @Test void anonymousBootstrapNeedsNoFabricatedTenantAndExpiryUsesTheDatabaseWindow(){
        var previous=SecurityContextHolder.getContext();SecurityContextHolder.clearContext();
        try(var ignored=TenantEntryScope.beginRequest()){
            assertThat(TenantEntryScope.optional()).isEmpty();var a=node();a.check("192.0.2.4",APP);
            String key=guards.selectById(1L).getKeyId();jdbc.update("UPDATE openapi_login_budget SET expires_at=?",java.sql.Timestamp.from(Instant.EPOCH));
            node().check("192.0.2.4",APP);assertThat(rows.selectList(null)).hasSize(2).allSatisfy(row->{assertThat(row.getAttemptCount()).isEqualTo(1);assertThat(row.getExpiresAt()).isAfter(rows.now());});
            assertThat(guards.selectById(1L).getKeyId()).isEqualTo(key);
        }finally{SecurityContextHolder.setContext(previous);}
    }
    @Test void fullPlatformCapacityRetainsExistingBudgetsAndCleansOnlyBoundedExpiredRows(){
        var a=node();a.check("192.0.2.5",APP);
        var expires=java.sql.Timestamp.from(rows.now().plusSeconds(300));
        var fake=IntStream.range(0,9998).mapToObj(index->new Object[]{1_000_000_000L+index,"f".repeat(48)+String.format("%016x",index),expires}).toList();
        jdbc.batchUpdate("INSERT INTO openapi_login_budget(id,scope,subject_hash,attempt_count,expires_at,created_at,created_by,updated_at,updated_by,deleted) VALUES(?,'PAIR',?,0,?,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)",fake);
        assertThat(rows.selectCount(null)).isEqualTo(10000);rateLimited(()->a.check("192.0.2.6",APP));a.check("192.0.2.5",APP);
        assertThat(rows.selectCount(null)).isEqualTo(10000);
        jdbc.update("UPDATE openapi_login_budget SET expires_at=? WHERE id BETWEEN 1000000000 AND 1000009997",java.sql.Timestamp.from(Instant.EPOCH));
        node().check("192.0.2.6",APP);assertThat(rows.selectCount(null)).isEqualTo(9602);
    }
    @Test void corruptedFingerprintMaterialFailsClosedWithoutResettingOrClaimingBudgets(){
        var a=node();a.check("192.0.2.7",APP);var original=guards.selectById(1L).getKeyCipher();
        try{
            jdbc.update("UPDATE openapi_login_budget_guard SET key_cipher=? WHERE id=1","corrupt");
            assertThatThrownBy(()->node().check("192.0.2.7",APP)).isInstanceOfSatisfying(BizException.class,error->assertThat(error.getErrorCode()).isEqualTo(OpenapiErrorCode.SECRET_STORAGE));
            assertThat(counter("PAIR").getAttemptCount()).isEqualTo(1);
        }finally{jdbc.update("UPDATE openapi_login_budget_guard SET key_cipher=? WHERE id=1",original);}
        a.check("192.0.2.7",APP);assertThat(counter("PAIR").getAttemptCount()).isEqualTo(2);
    }
    @Test void keyGenerationChangingAfterMaterialLoadCannotClaimUsingTheOldDigest(){
        var a=node();a.check("192.0.2.8",APP);jdbc.update("DELETE FROM openapi_login_budget");
        String original=guards.selectById(1L).getKeyId();var changed=new AtomicBoolean();
        PlatformTransactionManager rotating=new PlatformTransactionManager(){
            public TransactionStatus getTransaction(TransactionDefinition definition){
                var status=transactions.getTransaction(definition);
                if(!definition.isReadOnly()&&changed.compareAndSet(false,true))jdbc.update("UPDATE openapi_login_budget_guard SET key_id=? WHERE id=1",UUID.randomUUID().toString());
                return status;
            }
            public void commit(TransactionStatus status){transactions.commit(status);}
            public void rollback(TransactionStatus status){transactions.rollback(status);}
        };
        var stale=new OpenLoginThrottle(rows,guards,secrets,rotating);
        assertThatThrownBy(()->stale.check("192.0.2.8",APP)).isInstanceOfSatisfying(BizException.class,error->assertThat(error.getErrorCode()).isEqualTo(OpenapiErrorCode.SECRET_STORAGE));
        assertThat(rows.selectCount(null)).isZero();assertThat(guards.selectById(1L).getKeyId()).isEqualTo(original);
    }
    @Test void missingKeyCannotBeRecreatedToBypassExistingCounters(){
        var a=node();a.check("192.0.2.9",APP);var original=guards.selectById(1L);
        try{
            jdbc.update("DELETE FROM openapi_login_budget_guard WHERE id=1");
            assertThatThrownBy(()->node().check("192.0.2.9",APP)).isInstanceOfSatisfying(BizException.class,error->assertThat(error.getErrorCode()).isEqualTo(OpenapiErrorCode.SECRET_STORAGE));
            assertThat(guards.selectById(1L)).isNull();assertThat(counter("PAIR").getAttemptCount()).isEqualTo(1);
        }finally{guards.insert(original);}
    }
}
