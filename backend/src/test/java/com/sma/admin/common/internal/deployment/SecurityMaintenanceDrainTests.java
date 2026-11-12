package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sma.admin.support.DeploymentWorkFixture.host;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.auth.SessionEndedEvent;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.openapi.internal.persistence.*;
import com.sma.admin.openapi.internal.service.NonceMaintenance;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityMaintenanceDrainTests {
    @Autowired DeploymentWorkCoordinator admission;
    @Autowired PlatformTransactionManager manager;
    @Autowired SessionMapper sessions;
    @Autowired SecurityMaintenance security;
    @Autowired NonceMapper nonces;

    @Test void realExpiryTransactionsCommitDuringDrainButNextUnitsPreserveTheirRows()throws Exception{
        long current=97250,next=97251;seed(current);seed(next);
        var committing=new CountDownLatch(2);var release=new CountDownLatch(1);
        var gated=new PlatformTransactionManager(){
            public TransactionStatus getTransaction(TransactionDefinition definition){return manager.getTransaction(definition);}
            public void commit(TransactionStatus status){
                committing.countDown();
                try{if(!release.await(3,TimeUnit.SECONDS)){manager.rollback(status);throw new AssertionError("Commit gate not released");}}
                catch(InterruptedException interrupted){manager.rollback(status);Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
                manager.commit(status);
            }
            public void rollback(TransactionStatus status){manager.rollback(status);}
        };
        var events=mock(ApplicationEventPublisher.class);var executor=Executors.newFixedThreadPool(2);
        try(var auth=new SessionMaintenance(sessions,events,gated,security,(after,limit)->List.of(),new SessionMaintenance.Settings(false,2,1000L),admission);
            var nonce=new NonceMaintenance(nonces,(after,limit)->List.of(),new NonceMaintenance.Settings(false,2,1000L),gated,admission)){
            var a=executor.submit(()->List.of(auth.cleanTenant(current),auth.cleanTenant(next)));
            var b=executor.submit(()->List.of(nonce.cleanTenant(current),nonce.cleanTenant(next)));
            try{
                assertThat(committing.await(2,TimeUnit.SECONDS)).isTrue();assertThat(admission.snapshot().activeWork()).isEqualTo(2);
                assertThat(host(admission::beginDrain)).isTrue();assertThat(host(admission::tryStart)).isEmpty();
            }finally{release.countDown();}
            assertThat(a.get(5,TimeUnit.SECONDS)).containsExactly(1,0);assertThat(b.get(5,TimeUnit.SECONDS)).containsExactly(1,0);
            assertThat(admission.snapshot().activeWork()).isZero();
            assertThat(job(current,()->sessions.selectCount(null))).isEqualTo(1);assertThat(job(current,()->nonces.selectCount(null))).isEqualTo(1);
            assertThat(job(next,()->sessions.selectCount(null))).isEqualTo(2);assertThat(job(next,()->nonces.selectCount(null))).isEqualTo(2);
            verify(events,times(1)).publishEvent(any(SessionEndedEvent.class));
            assertThat(host(()->auth.cleanTenant(next))).isZero();assertThat(host(()->nonce.cleanTenant(next))).isZero();
        }finally{release.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    private void seed(long tenant){job(tenant,()->new TransactionTemplate(manager).execute(status->{
        for(boolean expired:List.of(true,false)){
            Instant now=Instant.now(),expiry=now.plusSeconds(expired?-60:3600);
            var session=new SessionEntity();session.setUserId(101L);session.setPortal("admin");
            session.setTokenHash(UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));
            session.setCredentialVersion(0L);session.setExpiresAt(expiry);session.setLastSeenAt(now);session.setLocked(false);sessions.insert(session);
            var nonce=new NonceEntity();nonce.setApplicationId(101L);nonce.setNonce(UUID.randomUUID().toString());nonce.setExpiresAt(expiry);nonces.insert(nonce);
        }return null;
    }));}
    private <T>T job(long tenant,Supplier<T> action){return ActorContext.withinJob(tenant,"maintenance-drain-test",action);}
}
