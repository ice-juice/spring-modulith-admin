package com.sma.admin.defense.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.security.*;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.defense.SecurityEvents;
import com.sma.admin.support.*;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class SecurityCollectionMysqlTests {
    @TempDir Path temp;
    @Autowired SecurityEventStore store;@Autowired SecurityEvents events;@Autowired SecuritySignalCapture capture;
    @Autowired DeploymentWork admission;@Autowired JsonMapper json;@Autowired SecuritySignals defaultSignals;
    @Autowired javax.sql.DataSource datasource;
    private SmaPrincipal viewer(long tenant){return new SmaPrincipal(997001,"collection-fixture",tenant,"admin",null,false,DataAccess.unrestricted(),Set.of("defense:event:view"),"UTC");}
    private SecuritySignal signal(long tenant){return Users.as(viewer(tenant),()->capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED));}
    private DefenseCollectionProperties settings()throws Exception{
        Path key=Files.write(temp.resolve("key"),new byte[32]);
        return new DefenseCollectionProperties(true,temp.resolve("spool").toString(),key.toString(),8,16,32768L,100,4,Duration.ofMillis(250),"integration.node-a");
    }
    private <T>T host(Supplier<T> action)throws Exception{
        try(var worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){return worker.submit(action::get).get(30,TimeUnit.SECONDS);}
    }
    private void cycle(SecurityCollectionWorker worker)throws Exception{host(()->{worker.cycle();return null;});}
    @Test void actualDatabaseConstraintFailureKeepsEncryptedEvidenceThenRestartStoresOriginalOnce()throws Exception{
        var p=settings();var signal=signal(9181);var clock=new AtomicLong();var queue=new SecurityCollectionQueue(p,clock::get);
        queue.offer(signal.fact(),f->signal);
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(datasource);
        jdbc.execute("ALTER TABLE defense_tenant_event ADD CONSTRAINT ck_collection_fixture_failure CHECK (tenant_id<>9181)");
        try(var worker=new SecurityCollectionWorker(p,queue,admission,store::append,json,clock::get)){
            cycle(worker);assertThat(worker.diskRecords()).isEqualTo(1);assertThat(queue.size()).isZero();assertThat(worker.failures.sum()).isEqualTo(1);
        }finally{jdbc.execute("ALTER TABLE defense_tenant_event DROP CHECK ck_collection_fixture_failure");}
        assertThat(Users.as(viewer(9181),()->events.tenant(signal.eventId()))).isEmpty();
        try(var worker=new SecurityCollectionWorker(p,new SecurityCollectionQueue(p,clock::get),admission,store::append,json,clock::get)){
            cycle(worker);assertThat(worker.stored.sum()).isEqualTo(1);assertThat(worker.diskRecords()).isZero();
        }
        assertThat(Users.as(viewer(9181),()->events.tenant(100))).hasSize(1);
        assertThat(Users.as(viewer(9181),()->events.tenant(signal.eventId()).orElseThrow().signal())).isEqualTo(signal);
        assertThat(Path.of(p.directory()).resolve(signal.eventId()+".ready")).doesNotExist();
    }
    @Test void actualCommittedDatabaseWriteWithLostReplyIsDeduplicatedAfterWorkerReopen()throws Exception{
        var p=settings();var signal=signal(9182);var clock=new AtomicLong();var queue=new SecurityCollectionQueue(p,clock::get);
        queue.offer(signal.fact(),f->signal);
        try(var worker=new SecurityCollectionWorker(p,queue,admission,s->{assertThat(store.append(s)).isEqualTo(SecurityEventStore.Result.STORED);throw new IllegalStateException("after commit response lost");},json,clock::get)){
            cycle(worker);assertThat(worker.diskRecords()).isEqualTo(1);
        }
        var original=Users.as(viewer(9182),()->events.tenant(signal.eventId()).orElseThrow());
        try(var worker=new SecurityCollectionWorker(p,new SecurityCollectionQueue(p,clock::get),admission,store::append,json,clock::get)){
            cycle(worker);assertThat(worker.duplicates.sum()).isEqualTo(1);assertThat(worker.diskRecords()).isZero();
        }
        assertThat(Users.as(viewer(9182),()->events.tenant(100))).containsExactly(original);
    }
    @Test void typedServiceCapturesCurrentTenantAndAsyncHostPersistsWithActualDeploymentPermit()throws Exception{
        assertThat(defaultSignals.observe(SecuritySignal.Fact.AUTHORIZATION_REJECTED)).isEqualTo(SecuritySignals.Admission.DISABLED);
        var p=settings();var meters=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var service=new SecurityCollectionService(p,capture,admission,store,json,meters);
        try{
            assertThat(Users.as(viewer(9183),()->service.observe(SecuritySignal.Fact.AUTHORIZATION_REJECTED))).isEqualTo(SecuritySignals.Admission.QUEUED_VOLATILE);
            assertThat(Users.as(viewer(9183),()->events.tenant(100))).isEmpty();
            host(()->{service.tick();return null;});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);List<SecurityEvents.View> rows;
            do{rows=Users.as(viewer(9183),()->events.tenant(100));if(!rows.isEmpty())break;Thread.sleep(20);}while(System.nanoTime()<deadline);
            assertThat(rows).hasSize(1);assertThat(rows.getFirst().signal().attribution().tenantId()).isEqualTo(9183);
            var id=rows.getFirst().eventId();assertThat(Users.as(viewer(9184),()->events.tenant(id))).isEmpty();
        }finally{service.close();}
        assertThat(admission.snapshot().activeWork()).isZero();
        assertThat(meters.get("sma.defense.collection").tag("result","stored").functionCounter().count()).isEqualTo(1);
    }
}
