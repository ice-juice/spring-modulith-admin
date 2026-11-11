package com.sma.admin.auth.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.SessionEndedEvent;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class SessionMaintenanceSweepTests {
    @Autowired SessionMapper sessions;@Autowired SecurityMaintenance security;@Autowired PlatformTransactionManager manager;
    private <T>T job(long tenant,Supplier<T> work){return ActorContext.withinJob(tenant,"auth-maintenance:test",work);}
    private List<Long> seed(long tenant,int expired){return job(tenant,()->new TransactionTemplate(manager).execute(status->{
        var ids=new ArrayList<Long>();var now=sessions.now();for(int i=0;i<expired+1;i++){
            var row=new SessionEntity();row.setUserId(101L);row.setPortal("admin");row.setTokenHash(UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));
            row.setCredentialVersion(0L);row.setExpiresAt(now.plusSeconds(i<expired?-60:3600));row.setLastSeenAt(now);row.setLocked(false);sessions.insert(row);ids.add(row.getId());
        }return ids;
    }));}
    @Test void realSweepFindsNeverVisitedInactiveTenantAndPreservesUnexpiredSessions(){
        seed(90203,1);seed(90204,1);
        var entries=List.of(new TenantWorkQuery.Entry(90203,false),new TenantWorkQuery.Entry(90204,true));
        TenantWorkQuery directory=(after,limit)->entries.stream().filter(entry->entry.tenantId()>after).limit(limit).toList();
        var events=mock(ApplicationEventPublisher.class);
        try(var maintenance=new SessionMaintenance(sessions,events,manager,security,directory,new SessionMaintenance.Settings(true,2,1000L))){
            maintenance.pollOnce();maintenance.pollOnce();
            assertThat(job(90203,()->sessions.selectCount(null))).isEqualTo(1);assertThat(job(90204,()->sessions.selectCount(null))).isEqualTo(1);
            verify(events,times(2)).publishEvent(any(SessionEndedEvent.class));
        }
    }
    @Test void boundedSweepSkipsAnotherNodesLockedRowAndRepeatedSweepIsIdempotent()throws Exception{
        var ids=seed(90205,201);var events=mock(ApplicationEventPublisher.class);var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var maintenance=new SessionMaintenance(sessions,events,manager,security,(after,limit)->List.of(),new SessionMaintenance.Settings(false,2,1000L));var pool=Executors.newSingleThreadExecutor()){
            var holder=pool.submit(()->job(90205,()->new TransactionTemplate(manager).execute(status->{
                sessions.selectOne(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getId,ids.getFirst()).last("FOR UPDATE"));held.countDown();
                try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Test holder deadline");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}return null;
            })));
            try{assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();assertThat(maintenance.cleanTenant(90205)).isEqualTo(200);}
            finally{release.countDown();holder.get(5,TimeUnit.SECONDS);}
            assertThat(maintenance.cleanTenant(90205)).isEqualTo(1);assertThat(maintenance.cleanTenant(90205)).isZero();
            assertThat(job(90205,()->sessions.selectCount(null))).isEqualTo(1);verify(events,times(201)).publishEvent(any(SessionEndedEvent.class));
        }
    }
}
