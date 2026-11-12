package com.sma.admin.defense.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@IntegrationTest
@TestPropertySource(properties={"sma.defense.maintenance.enabled=false","sma.defense.storage.cleanup-batch=2","sma.defense.storage.retention=1h"})
class SecurityRetentionMysqlTests {
    @Autowired SecurityEventStore store; @Autowired SecuritySignalCapture capture; @Autowired TenantWorkQuery directory;
    @Autowired DeploymentWork admission; @Autowired DataSource datasource; @Autowired DefenseMaintenanceProperties configured;
    private final List<SecuritySignal> created=new ArrayList<>();
    private JdbcTemplate jdbc(){return new JdbcTemplate(datasource);}
    private SmaPrincipal actor(long tenant){return new SmaPrincipal(941001,"retention-fixture",tenant,"admin",null,false,DataAccess.unrestricted(),Set.of(),"UTC");}
    private <T>T host(Supplier<T> work)throws Exception{
        try(var pool=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){
            try{return pool.submit(work::get).get(30,TimeUnit.SECONDS);}catch(ExecutionException failure){if(failure.getCause() instanceof RuntimeException cause)throw cause;throw failure;}
        }
    }
    private SecuritySignal create(Long tenant,boolean expired,boolean held)throws Exception{
        var signal=tenant==null?host(()->capture.capture(SecuritySignal.Fact.TENANT_ENTRY_REJECTED)):
                Users.as(actor(tenant),()->capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED));
        created.add(signal);assertThat(host(()->store.append(signal))).isEqualTo(SecurityEventStore.Result.STORED);
        if(held)host(()->tenant==null?store.holdPlatform(signal.eventId(),true):store.holdTenant(tenant,signal.eventId(),true));
        if(expired)expire(signal);return signal;
    }
    private String table(SecuritySignal signal){return signal.attribution().scope()==SecuritySignal.Scope.PLATFORM?"defense_platform_event":"defense_tenant_event";}
    private void expire(SecuritySignal signal){jdbc().update("UPDATE "+table(signal)+" SET expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE event_id=?",signal.eventId().toString());}
    private boolean present(SecuritySignal signal){return jdbc().queryForObject("SELECT COUNT(*) FROM "+table(signal)+" WHERE event_id=?",Integer.class,signal.eventId().toString())==1;}
    private SecurityRetentionCycle cycle(){return new SecurityRetentionCycle(new DefenseMaintenanceProperties(true,Duration.ofSeconds(1),100),admission,directory,store::prunePlatform,store::pruneTenant);}
    @AfterEach void cleanup()throws Exception{
        for(var signal:created){Long tenant=signal.attribution().tenantId();host(()->tenant==null?store.holdPlatform(signal.eventId(),false):store.holdTenant(tenant,signal.eventId(),false));expire(signal);}
        for(int i=0;i<=created.size();i++){
            host(store::prunePlatform);
            for(long tenant:created.stream().map(s->s.attribution().tenantId()).filter(Objects::nonNull).distinct().toList())host(()->store.pruneTenant(tenant));
            if(created.stream().noneMatch(this::present))break;
        }
        assertThat(created).noneMatch(this::present);assertCounters();
    }
    private void assertCounters(){
        assertThat(jdbc().queryForObject("SELECT records FROM defense_capacity WHERE id=1",Long.class)).isEqualTo(jdbc().queryForObject("SELECT COUNT(*) FROM defense_platform_event",Long.class));
        assertThat(jdbc().queryForObject("SELECT records FROM defense_capacity WHERE id=2",Long.class)).isEqualTo(jdbc().queryForObject("SELECT COUNT(*) FROM defense_tenant_event",Long.class));
        for(long tenant:created.stream().map(s->s.attribution().tenantId()).filter(Objects::nonNull).distinct().toList()){
            long count=jdbc().queryForObject("SELECT COUNT(*) FROM defense_tenant_event WHERE tenant_id=?",Long.class,tenant);
            var guards=jdbc().queryForList("SELECT records FROM defense_tenant_capacity WHERE tenant_id=?",Long.class,tenant);
            if(count==0)assertThat(guards).isEmpty();else assertThat(guards).containsExactly(count);
        }
    }
    @Test void actualExecutorDeletesOnlyExpiredUnheldEvidenceIncludingPausedTenant()throws Exception{
        assertThat(configured.enabled()).isFalse();var pExpired=create(null,true,false);var pHeld=create(null,true,true);var pLive=create(null,false,false);
        var tExpired=create(9411L,true,false);var tHeld=create(9411L,true,true);var tLive=create(9411L,false,false);var other=create(9412L,true,false);
        jdbc().update("UPDATE tenant_tenant SET status='SUSPENDED' WHERE id=9411");
        assertThat(host(()->directory.page(9400,100))).anyMatch(entry->entry.tenantId()==9411&&!entry.acceptsBusiness());
        var meters=new SimpleMeterRegistry();var service=new SecurityRetentionService(new DefenseMaintenanceProperties(true,Duration.ofSeconds(1),100),admission,directory,store,meters);
        try{
            host(()->{service.tick();return null;});await().atMost(Duration.ofSeconds(15)).until(()->meters.get("sma.defense.maintenance.running").gauge().value()==0);
            assertThat(present(pExpired)).isFalse();assertThat(present(tExpired)).isFalse();assertThat(present(other)).isFalse();
            for(var retained:List.of(pHeld,pLive,tHeld,tLive))assertThat(present(retained)).isTrue();
            assertThat(meters.get("sma.defense.maintenance").tag("result","platform-deleted").functionCounter().count()).isEqualTo(1);
            assertThat(meters.get("sma.defense.maintenance").tag("result","tenant-deleted").functionCounter().count()).isEqualTo(2);
            assertThat(admission.snapshot().activeWork()).isZero();assertCounters();
            create(9411L,false,false);assertCounters(); // Reclaimed capacity can be used without changing held evidence.
        }finally{service.close();}
    }
    @Test void twoRealWorkersRespectPerBatchBoundAndNeverDoubleRefundCapacity()throws Exception{
        for(int i=0;i<5;i++){create(null,true,false);create(9413L,true,false);}
        var held=create(9413L,true,true);var a=cycle();var b=cycle();var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2,Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){
            var first=workers.submit(()->{start.await();a.cycle();return null;});var second=workers.submit(()->{start.await();b.cycle();return null;});start.countDown();
            first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
        }
        assertThat(a.platformDeleted.sum()+b.platformDeleted.sum()).isEqualTo(4);assertThat(a.tenantDeleted.sum()+b.tenantDeleted.sum()).isEqualTo(4);
        assertThat(present(held)).isTrue();assertCounters();
        host(()->{cycle().cycle();return null;});assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM defense_platform_event",Integer.class)).isZero();
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM defense_tenant_event WHERE tenant_id=9413",Integer.class)).isEqualTo(1);assertCounters();
    }
    @Test void realCleanupFailureRollsBackRowsAndCountersWhileOtherScopesContinue()throws Exception{
        var first=create(null,true,false);var second=create(null,true,false);var held=create(null,true,true);var tenant=create(9414L,true,false);
        jdbc().execute("ALTER TABLE defense_capacity ADD CONSTRAINT ck_retention_fixture_failure CHECK (id<>1 OR records<>1)");
        var cycle=cycle();
        try{
            host(()->{cycle.cycle();return null;});assertThat(cycle.failures.sum()).isEqualTo(1);
            assertThat(present(first)).isTrue();assertThat(present(second)).isTrue();assertThat(present(held)).isTrue();assertThat(present(tenant)).isFalse();assertCounters();
        }finally{jdbc().execute("ALTER TABLE defense_capacity DROP CHECK ck_retention_fixture_failure");}
        host(()->{cycle.cycle();return null;});assertThat(present(first)).isFalse();assertThat(present(second)).isFalse();assertThat(present(held)).isTrue();assertCounters();
    }
}
