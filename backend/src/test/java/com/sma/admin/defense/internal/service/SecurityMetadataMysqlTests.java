package com.sma.admin.defense.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.sma.admin.common.security.*;
import com.sma.admin.defense.SecurityEvents;
import com.sma.admin.support.*;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class SecurityMetadataMysqlTests {
    @Autowired SecurityEventStore store; @Autowired SecurityEvents events; @Autowired SecuritySignalCapture capture;
    @Autowired JsonMapper json; @Autowired DataSource datasource; @TempDir Path temp;
    record OldSignal(UUID eventId,UUID correlationId,Instant occurredAt,SecuritySignal.Fact fact,
            SecuritySignal.Attribution attribution,RequestSource source) {}
    private SmaPrincipal viewer(long tenant){return new SmaPrincipal(921001,"metadata-fixture",tenant,"admin",null,false,DataAccess.unrestricted(),Set.of("defense:event:view"),"UTC");}
    private SecuritySignal signal(long tenant){return Users.as(viewer(tenant),()->capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED));}
    private SecuritySignal old(SecuritySignal s){return new SecuritySignal(s.eventId(),s.correlationId(),s.occurredAt(),s.fact(),s.attribution(),s.source());}
    private <T>T host(Supplier<T> work)throws Exception{
        try(var pool=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){return pool.submit(work::get).get(30,TimeUnit.SECONDS);}
    }
    @Test void legacyStoredBytesReplayWithoutChangingDigestAndMetadataChangesConflict()throws Exception{
        long tenant=9210;var current=signal(tenant);var legacy=old(current);
        String expected=json.writeValueAsString(new OldSignal(legacy.eventId(),legacy.correlationId(),legacy.occurredAt(),legacy.fact(),legacy.attribution(),legacy.source()));
        assertThat(host(()->store.append(legacy))).isEqualTo(SecurityEventStore.Result.STORED);
        var jdbc=new JdbcTemplate(datasource);var before=jdbc.queryForMap("SELECT payload,payload_hash,received_at,expires_at FROM defense_tenant_event WHERE tenant_id=? AND event_id=?",tenant,legacy.eventId().toString());
        assertThat(before.get("payload")).isEqualTo(expected);
        var restored=json.readValue(expected,SecuritySignal.class);assertThat(restored.metadata()).isNull();
        assertThat(host(()->store.append(restored))).isEqualTo(SecurityEventStore.Result.DUPLICATE);
        assertThat(host(()->store.append(current))).isEqualTo(SecurityEventStore.Result.CONFLICT);
        assertThat(jdbc.queryForMap("SELECT payload,payload_hash,received_at,expires_at FROM defense_tenant_event WHERE tenant_id=? AND event_id=?",tenant,legacy.eventId().toString())).isEqualTo(before);
        assertThat(Users.as(viewer(tenant),()->events.tenant(legacy.eventId()).orElseThrow().signal().metadata())).isNull();
        var next=signal(tenant);assertThat(host(()->store.append(next))).isEqualTo(SecurityEventStore.Result.STORED);
        assertThat(Users.as(viewer(tenant),()->events.tenant(next.eventId()).orElseThrow().signal())).isEqualTo(next);
        assertThat(Users.as(viewer(tenant+1),()->events.tenant(next.eventId()))).isEmpty();
    }
    @Test void mixedV1V2SpoolReopenAfterDatabaseCommitWithoutAckOnlyDeduplicates()throws Exception{
        long tenant=9212;var legacy=old(signal(tenant));var current=signal(tenant);
        byte[] key=new byte[32];Path directory=temp.resolve("mixed-spool");
        try(var spool=new EncryptedSignalSpool(directory,key,8,32768,2,4096,json)){
            assertThat(spool.offer(legacy)).isEqualTo(EncryptedSignalSpool.Offer.STORED);
            assertThat(spool.offer(current)).isEqualTo(EncryptedSignalSpool.Offer.STORED);
            assertThat(host(()->store.append(spoolRead(spool,legacy.eventId())))).isEqualTo(SecurityEventStore.Result.STORED);
            assertThat(host(()->store.append(spoolRead(spool,current.eventId())))).isEqualTo(SecurityEventStore.Result.STORED);
            // Simulate lost commit acknowledgements: neither file is acknowledged before reopening.
        }
        try(var reopened=new EncryptedSignalSpool(directory,key,8,32768,2,4096,json)){
            assertThat(reopened.snapshot().records()).isEqualTo(2);
            for(var original:List.of(legacy,current)){
                var replay=reopened.read(original.eventId());assertThat(replay).isEqualTo(original);
                assertThat(host(()->store.append(replay))).isEqualTo(SecurityEventStore.Result.DUPLICATE);reopened.acknowledge(replay.eventId());
            }
            assertThat(reopened.snapshot().records()).isZero();
        }
        assertThat(new JdbcTemplate(datasource).queryForObject("SELECT COUNT(*) FROM defense_tenant_event WHERE tenant_id=?",Integer.class,tenant)).isEqualTo(2);
    }
    private SecuritySignal spoolRead(EncryptedSignalSpool spool,UUID id){try{return spool.read(id);}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}}
}
