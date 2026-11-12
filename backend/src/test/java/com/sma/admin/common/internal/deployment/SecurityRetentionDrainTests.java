package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.*;
import com.sma.admin.common.security.*;
import com.sma.admin.defense.internal.persistence.*;
import com.sma.admin.defense.internal.service.*;
import com.sma.admin.support.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties="sma.defense.maintenance.enabled=false")
class SecurityRetentionDrainTests {
    @Autowired DeploymentWorkCoordinator work; @Autowired TenantWorkQuery directory; @Autowired SecurityEventStore original;
    @Autowired SecuritySignalCapture capture; @Autowired PlatformTransactionManager transactions; @Autowired DataSource datasource;
    @Autowired PlatformEventMapper platform; @Autowired TenantEventMapper tenant; @Autowired CapacityMapper capacity;
    @Autowired TenantCapacityMapper tenantCapacity; @Autowired DefenseStorageProperties settings; @Autowired JsonMapper json;
    private <T>T host(Supplier<T> action)throws Exception{
        try(var pool=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){return pool.submit(action::get).get(30,TimeUnit.SECONDS);}
    }
    @Test void committedCleanupKeepsActualPermitUntilReturnAndDrainPreservesNextTenant()throws Exception{
        var jdbc=new JdbcTemplate(datasource);var platformSignal=host(()->capture.capture(SecuritySignal.Fact.TENANT_ENTRY_REJECTED));
        var actor=new SmaPrincipal(941001,"drain-fixture",9415,"admin",null,false,DataAccess.unrestricted(),Set.of(),"UTC");
        var tenantSignal=Users.as(actor,()->capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED));
        host(()->original.append(platformSignal));host(()->original.append(tenantSignal));
        jdbc.update("UPDATE defense_platform_event SET expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE event_id=?",platformSignal.eventId().toString());
        jdbc.update("UPDATE defense_tenant_event SET expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE event_id=?",tenantSignal.eventId().toString());
        var committed=new CountDownLatch(1);var release=new CountDownLatch(1);
        var gated=new PlatformTransactionManager(){
            public TransactionStatus getTransaction(TransactionDefinition definition){return transactions.getTransaction(definition);}
            public void commit(TransactionStatus status){transactions.commit(status);committed.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Cleanup completion not released");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
            public void rollback(TransactionStatus status){transactions.rollback(status);}
        };
        var store=new SecurityEventStore(platform,tenant,capacity,tenantCapacity,settings,json,gated);
        var meters=new SimpleMeterRegistry();var service=new SecurityRetentionService(new DefenseMaintenanceProperties(true,Duration.ofSeconds(1),100),work,directory,store,meters);
        try{
            host(()->{service.tick();return null;});assertThat(committed.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(host(work::beginDrain)).isTrue();await().atMost(Duration.ofSeconds(3)).until(()->work.snapshot().activeWork()==1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM defense_platform_event WHERE event_id=?",Integer.class,platformSignal.eventId().toString())).isZero();
            assertThat(host(work::tryStart)).isEmpty();release.countDown();
            await().atMost(Duration.ofSeconds(10)).until(()->work.snapshot().activeWork()==0&&meters.get("sma.defense.maintenance.running").gauge().value()==0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM defense_tenant_event WHERE event_id=?",Integer.class,tenantSignal.eventId().toString())).isEqualTo(1);
            host(()->{service.tick();return null;});assertThat(meters.get("sma.defense.maintenance").tag("result","platform-deleted").functionCounter().count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT records FROM defense_tenant_capacity WHERE tenant_id=9415",Long.class)).isEqualTo(1);
        }finally{release.countDown();service.close();host(()->original.pruneTenant(9415));host(original::prunePlatform);}
    }
}
