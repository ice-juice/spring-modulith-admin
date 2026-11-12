package com.sma.admin.defense;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import com.sma.admin.common.security.*;
import com.sma.admin.defense.internal.service.SecurityEventStore;
import com.sma.admin.support.*;

@IntegrationTest
@org.junit.jupiter.api.parallel.Isolated("Owns the security event capacity fixture in the dedicated test schema")
@TestPropertySource(properties={"sma.tenant.platform-admin-user-ids=991","sma.defense.storage.maximum-platform-records=4",
        "sma.defense.storage.maximum-tenant-records=3","sma.defense.storage.maximum-tenant-total-records=8",
        "sma.defense.storage.cleanup-batch=2","sma.defense.storage.retention=1h"})
class SecurityEventStoreTests {
    @Autowired SecurityEventStore store;@Autowired SecurityEvents events;@Autowired SecuritySignalCapture capture;
    @Autowired DataSource datasource;@Autowired PlatformTransactionManager transactions;
    private static final Set<String> BOTH=Set.of("defense:event:view","defense:platform:view");
    private SmaPrincipal actor(long tenant,long user,boolean superAdmin,Set<String> permissions){return new SmaPrincipal(user,"test",tenant,"admin",null,superAdmin,DataAccess.unrestricted(),permissions,"UTC");}
    private SmaPrincipal viewer(long tenant){return actor(tenant,991,true,BOTH);}
    private <T>T host(Supplier<T> action){
        try(var pool=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){return pool.submit(action::get).get(30,TimeUnit.SECONDS);}
        catch(ExecutionException failure){if(failure.getCause() instanceof RuntimeException cause)throw cause;throw new IllegalStateException(failure.getCause());}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private SecuritySignal tenant(long id){return Users.as(viewer(id),()->capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED));}
    private SecuritySignal platform(){return host(()->capture.capture(SecuritySignal.Fact.TENANT_ENTRY_REJECTED));}
    private SecuritySignal time(SecuritySignal original,Instant at){return new SecuritySignal(original.eventId(),original.correlationId(),at,original.fact(),original.attribution(),original.source());}
    private void expire(String table,UUID id){new JdbcTemplate(datasource).update("UPDATE "+table+" SET expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE event_id=?",id.toString());}
    @org.junit.jupiter.api.BeforeEach void isolateCapacityFixture(){
        var jdbc=new JdbcTemplate(datasource);
        // These tests deliberately exhaust the entire module's tiny global budget. Earlier HTTP/collector
        // scenarios in the same serial reactor must not count against this independent fixture.
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class))
                .matches("sma_test_[a-z0-9_]+|springboot_modulith_admin_test");
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            jdbc.queryForList("SELECT id FROM defense_capacity ORDER BY id FOR UPDATE");
            jdbc.update("DELETE FROM defense_tenant_event");jdbc.update("DELETE FROM defense_platform_event");
            jdbc.update("DELETE FROM defense_tenant_capacity");jdbc.update("UPDATE defense_capacity SET records=0");
        });
    }
    @Test void committedEvidenceSurvivesBusinessRollbackAndDuplicateDoesNotExtendOrRewrite(){
        var s=tenant(9101);
        host(()->{
            new TransactionTemplate(transactions).executeWithoutResult(status->{assertThat(store.append(s)).isEqualTo(SecurityEventStore.Result.STORED);status.setRollbackOnly();});return null;
        });
        var saved=Users.as(viewer(9101),()->events.tenant(s.eventId()).orElseThrow());
        assertThat(saved.signal()).isEqualTo(s);assertThat(saved.ruleVersion()).isEqualTo(1);
        assertThat(host(()->store.append(s))).isEqualTo(SecurityEventStore.Result.DUPLICATE);
        var changed=new SecuritySignal(s.eventId(),s.correlationId(),s.occurredAt(),SecuritySignal.Fact.REQUEST_INPUT_REJECTED,s.attribution(),null);
        assertThat(host(()->store.append(changed))).isEqualTo(SecurityEventStore.Result.CONFLICT);
        assertThat(Users.as(viewer(9101),()->events.tenant(s.eventId()).orElseThrow())).isEqualTo(saved);
        expire("defense_tenant_event",s.eventId());assertThat(host(()->store.pruneTenant(9101))).isEqualTo(1);
    }
    @Test void platformAndTenantQueriesRequireCurrentExplicitAuthorityEvenForSuperAdmins(){
        var a=tenant(9102);var p=platform();host(()->{store.append(a);store.append(p);return null;});
        assertThat(Users.as(viewer(9102),()->events.tenant(a.eventId()))).isPresent();
        assertThat(Users.as(viewer(9103),()->events.tenant(a.eventId()))).isEmpty();
        assertThat(Users.as(viewer(1),()->events.tenant(a.eventId()))).isEmpty();
        assertThat(Users.as(viewer(1),()->events.platform(p.eventId()))).isPresent();
        assertThat(Users.as(viewer(9102),()->events.tenant(p.eventId()))).isEmpty();
        for(var denied:List.of(viewer(9102),actor(1,992,true,BOTH),actor(1,991,true,Set.of("defense:event:view"))))
            assertThatThrownBy(()->Users.as(denied,()->events.platform(p.eventId()))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->Users.as(actor(9102,991,true,Set.of()),()->events.tenant(a.eventId()))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->events.tenant(10)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->Users.as(viewer(9102),()->events.tenant(101))).isInstanceOf(IllegalArgumentException.class);
        expire("defense_tenant_event",a.eventId());expire("defense_platform_event",p.eventId());host(()->{store.pruneTenant(9102);store.prunePlatform();return null;});
    }
    @Test void hostOnlyWriteRejectsHttpAuthenticatedAndTenantJobs(){
        var s=tenant(9104);
        assertThatThrownBy(()->store.append(s)).isInstanceOf(AccessDeniedException.class); // Test thread has HTTP attributes.
        assertThatThrownBy(()->Users.as(viewer(9104),()->store.append(s))).isInstanceOf(AccessDeniedException.class);
        host(()->{assertThatThrownBy(()->ActorContext.withinJob(9104,"arbitrary",()->store.append(s))).isInstanceOf(AccessDeniedException.class);return null;});
        assertThat(Users.as(viewer(9104),()->events.tenant(s.eventId()))).isEmpty();
    }
    @Test void sameEnvelopeConcurrentCommitIsUniqueAndTenantCapacityIsAtomic()throws Exception{
        var s=tenant(9105);
        try(var pool=Executors.newFixedThreadPool(4,Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){
            var gate=new CountDownLatch(1);var futures=new ArrayList<Future<SecurityEventStore.Result>>();
            for(int i=0;i<4;i++)futures.add(pool.submit(()->{gate.await();return store.append(s);}));gate.countDown();
            var results=new ArrayList<SecurityEventStore.Result>();for(var f:futures)results.add(f.get(20,TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(SecurityEventStore.Result.STORED,SecurityEventStore.Result.DUPLICATE,SecurityEventStore.Result.DUPLICATE,SecurityEventStore.Result.DUPLICATE);
            var signals=new ArrayList<SecuritySignal>();for(int i=0;i<4;i++)signals.add(tenant(9105));futures.clear();
            for(var input:signals)futures.add(pool.submit(()->store.append(input)));results.clear();for(var f:futures)results.add(f.get(20,TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(SecurityEventStore.Result.STORED,SecurityEventStore.Result.STORED,SecurityEventStore.Result.CAPACITY,SecurityEventStore.Result.CAPACITY);
        }
        var rows=Users.as(viewer(9105),()->events.tenant(100));assertThat(rows).hasSize(3);
        for(var row:rows)expire("defense_tenant_event",row.eventId());
        assertThat(host(()->store.pruneTenant(9105))).isEqualTo(2);assertThat(host(()->store.pruneTenant(9105))).isEqualTo(1);
        assertThat(new JdbcTemplate(datasource).queryForObject("SELECT COUNT(*) FROM defense_tenant_capacity WHERE tenant_id=9105",Long.class)).isZero();
    }
    @Test void globalTenantBudgetCannotBeBypassedByCreatingNewTenants(){
        var all=new ArrayList<SecuritySignal>();for(long t=9110;t<9113;t++)for(int i=0;i<3;i++)all.add(tenant(t));
        for(int i=0;i<8;i++){var s=all.get(i);assertThat(host(()->store.append(s))).isEqualTo(SecurityEventStore.Result.STORED);}
        assertThat(host(()->store.append(all.get(8)))).isEqualTo(SecurityEventStore.Result.CAPACITY);
        var next=tenant(9113);assertThat(host(()->store.append(next))).isEqualTo(SecurityEventStore.Result.CAPACITY);
        assertThat(new JdbcTemplate(datasource).queryForObject("SELECT COUNT(*) FROM defense_tenant_capacity WHERE tenant_id=9113",Long.class)).isZero();
        for(var s:all)expire("defense_tenant_event",s.eventId());
        for(long t=9110;t<9113;t++){long id=t;host(()->{store.pruneTenant(id);store.pruneTenant(id);return null;});}
        assertThat(new JdbcTemplate(datasource).queryForObject("SELECT records FROM defense_capacity WHERE id=2",Long.class)).isZero();
    }
    @Test void platformRetentionIsBoundedPreservesHeldEvidenceAndRejectsExpiredReplay(){
        var signals=new ArrayList<SecuritySignal>();for(int i=0;i<5;i++)signals.add(platform());
        for(int i=0;i<4;i++){var s=signals.get(i);assertThat(host(()->store.append(s))).isEqualTo(SecurityEventStore.Result.STORED);}
        assertThat(host(()->store.append(signals.get(4)))).isEqualTo(SecurityEventStore.Result.CAPACITY);
        var held=signals.getFirst();assertThat(host(()->store.holdPlatform(held.eventId(),true))).isTrue();
        for(var s:signals)expire("defense_platform_event",s.eventId());
        assertThat(host(store::prunePlatform)).isEqualTo(2);assertThat(host(store::prunePlatform)).isEqualTo(1);
        assertThat(Users.as(viewer(1),()->events.platform(held.eventId()).orElseThrow()).held()).isTrue();
        host(()->store.holdPlatform(held.eventId(),false));assertThat(host(store::prunePlatform)).isEqualTo(1);
        assertThat(host(()->store.append(time(held,Instant.now().minusSeconds(3601))))).isEqualTo(SecurityEventStore.Result.EXPIRED);
        assertThatThrownBy(()->host(()->store.append(time(held,Instant.now().plusSeconds(120))))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new JdbcTemplate(datasource).queryForObject("SELECT records FROM defense_capacity WHERE id=1",Long.class)).isZero();
    }
    @Test void tenantPreservationCannotAffectAnotherTenantAndHeldRowsStillConsumeCapacity(){
        var s=tenant(9120);host(()->store.append(s));
        assertThat(host(()->store.holdTenant(9121,s.eventId(),true))).isFalse();
        assertThat(host(()->store.holdTenant(9120,s.eventId(),true))).isTrue();
        expire("defense_tenant_event",s.eventId());assertThat(host(()->store.pruneTenant(9120))).isZero();
        assertThat(Users.as(viewer(9120),()->events.tenant(s.eventId()).orElseThrow()).held()).isTrue();
        assertThat(new JdbcTemplate(datasource).queryForObject("SELECT records FROM defense_tenant_capacity WHERE tenant_id=9120",Long.class)).isEqualTo(1);
        host(()->store.holdTenant(9120,s.eventId(),false));assertThat(host(()->store.pruneTenant(9120))).isEqualTo(1);
    }
    @Test void databaseFailureRollsBackEventAndBothCountersBeforeSameEnvelopeCanBeRetried(){
        var s=tenant(9122);var jdbc=new JdbcTemplate(datasource);
        jdbc.execute("ALTER TABLE defense_tenant_capacity ADD CONSTRAINT ck_defense_fixture_failure CHECK (tenant_id<>9122 OR records=0)");
        try{assertThatThrownBy(()->host(()->store.append(s))).isInstanceOf(org.springframework.dao.DataAccessException.class);}
        finally{jdbc.execute("ALTER TABLE defense_tenant_capacity DROP CHECK ck_defense_fixture_failure");}
        assertThat(Users.as(viewer(9122),()->events.tenant(s.eventId()))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT records FROM defense_capacity WHERE id=2",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM defense_tenant_capacity WHERE tenant_id=9122",Long.class)).isZero();
        assertThat(host(()->store.append(s))).isEqualTo(SecurityEventStore.Result.STORED);
        expire("defense_tenant_event",s.eventId());host(()->store.pruneTenant(9122));
    }
}
