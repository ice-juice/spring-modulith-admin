package com.sma.admin.defense.internal.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.security.*;
import com.sma.admin.defense.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** Host-only trusted envelope persistence; not a callable HTTP ingestion endpoint. */
@Service
public class SecurityEventStore {
    public enum Result { STORED, DUPLICATE, CONFLICT, CAPACITY, EXPIRED }
    private final PlatformEventMapper platform;private final TenantEventMapper tenant;
    private final CapacityMapper capacity;private final TenantCapacityMapper tenantCapacity;
    private final DefenseStorageProperties settings;private final JsonMapper json;private final TransactionTemplate tx;
    public SecurityEventStore(PlatformEventMapper platform,TenantEventMapper tenant,CapacityMapper capacity,TenantCapacityMapper tenantCapacity,
            DefenseStorageProperties settings,JsonMapper json,PlatformTransactionManager transactions){
        this.platform=platform;this.tenant=tenant;this.capacity=capacity;this.tenantCapacity=tenantCapacity;this.settings=settings;this.json=json;
        tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(3);
    }
    public Result append(SecuritySignal signal){
        requireWorker();Objects.requireNonNull(signal);
        // A database result never overrides provenance. Only the later capture/queue adapter may supply envelopes.
        String payload=json.writeValueAsString(signal);byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>2048)throw new IllegalArgumentException("Signal exceeds storage budget");
        String hash=digest(bytes);
        return tx.execute(status->{
            Instant now=capacity.now(),occurred=signal.occurredAt().truncatedTo(ChronoUnit.MILLIS);
            if(occurred.isAfter(now.plusSeconds(60)))throw new IllegalArgumentException("Signal time exceeds allowed skew");
            Instant expiry=occurred.plus(settings.retention());
            if(!expiry.isAfter(now))return Result.EXPIRED;
            if(signal.attribution().scope()==SecuritySignal.Scope.PLATFORM){
                var guard=capacity.lock(1);
                var prior=platform.selectOne(new LambdaQueryWrapper<PlatformEventEntity>().eq(PlatformEventEntity::getEventId,signal.eventId().toString()));
                if(prior!=null)return prior.getPayloadHash().equals(hash)?Result.DUPLICATE:Result.CONFLICT;
                if(guard.getRecords()>=settings.maximumPlatformRecords())return Result.CAPACITY;
                var row=new PlatformEventEntity();fill(row,signal,payload,hash,now,expiry);platform.insert(row);adjust(1,1);return Result.STORED;
            }
            var global=capacity.lock(2);long tenantId=signal.attribution().tenantId();
            return ActorContext.withinJob(tenantId,"defense-store",()->{
                var prior=tenant.selectOne(new LambdaQueryWrapper<TenantEventEntity>().eq(TenantEventEntity::getEventId,signal.eventId().toString()));
                if(prior!=null)return prior.getPayloadHash().equals(hash)?Result.DUPLICATE:Result.CONFLICT;
                // Check total before allocating a new tenant guard, preventing unbounded guard growth at saturation.
                if(global.getRecords()>=settings.maximumTenantTotalRecords())return Result.CAPACITY;
                tenantCapacity.ensure(IdWorker.getId(),tenantId);var guard=tenantCapacity.lock(tenantId);
                if(guard.getRecords()>=settings.maximumTenantRecords())return Result.CAPACITY;
                var row=new TenantEventEntity();fill(row,signal,payload,hash,now,expiry);tenant.insert(row);
                adjust(2,1);if(tenantCapacity.adjust(guard.getId(),1)!=1)throw new IllegalStateException("Tenant capacity accounting failed");
                return Result.STORED;
            });
        });
    }
    public int prunePlatform(){
        requireWorker();return tx.execute(status->{
            capacity.lock(1);var now=capacity.now();
            var rows=platform.selectList(new LambdaQueryWrapper<PlatformEventEntity>().eq(PlatformEventEntity::getHeld,false)
                .le(PlatformEventEntity::getExpiresAt,now).orderByAsc(PlatformEventEntity::getExpiresAt,PlatformEventEntity::getId)
                .last("LIMIT "+settings.cleanupBatch()+" FOR UPDATE"));
            int removed=0;for(var row:rows)removed+=platform.purge(row.getId(),now);adjust(1,-removed);return removed;
        });
    }
    public int pruneTenant(long tenantId){
        requireWorker();if(tenantId<=0)throw new IllegalArgumentException("Tenant must be positive");
        return tx.execute(status->{capacity.lock(2);return ActorContext.withinJob(tenantId,"defense-retention",()->{
            var guard=tenantCapacity.lock(tenantId);if(guard==null)return 0;var now=capacity.now();
            var rows=tenant.selectList(new LambdaQueryWrapper<TenantEventEntity>().eq(TenantEventEntity::getHeld,false)
                .le(TenantEventEntity::getExpiresAt,now).orderByAsc(TenantEventEntity::getExpiresAt,TenantEventEntity::getId)
                .last("LIMIT "+settings.cleanupBatch()+" FOR UPDATE"));
            int removed=0;for(var row:rows)removed+=tenant.purge(row.getId(),now);adjust(2,-removed);
            if(tenantCapacity.adjust(guard.getId(),-removed)!=1)throw new IllegalStateException("Tenant capacity accounting failed");
            tenantCapacity.removeEmpty(guard.getId());return removed;
        });});
    }
    /** Host-only preservation primitive. An authorized, audited management protocol is a later delivery. */
    public boolean holdPlatform(UUID eventId,boolean hold){
        requireWorker();Objects.requireNonNull(eventId);return tx.execute(status->{capacity.lock(1);
            var row=platform.selectOne(new LambdaQueryWrapper<PlatformEventEntity>().eq(PlatformEventEntity::getEventId,eventId.toString()).last("FOR UPDATE"));
            return row!=null && platform.hold(row.getId(),hold)==1;
        });
    }
    public boolean holdTenant(long tenantId,UUID eventId,boolean hold){
        requireWorker();if(tenantId<=0)throw new IllegalArgumentException("Tenant must be positive");Objects.requireNonNull(eventId);
        return tx.execute(status->{capacity.lock(2);return ActorContext.withinJob(tenantId,"defense-preserve",()->{
            var row=tenant.selectOne(new LambdaQueryWrapper<TenantEventEntity>().eq(TenantEventEntity::getEventId,eventId.toString()).last("FOR UPDATE"));
            return row!=null && tenant.hold(row.getId(),hold)==1;
        });});
    }
    private void adjust(long id,long delta){if(capacity.adjust(id,delta)!=1)throw new IllegalStateException("Event capacity accounting failed");}
    private static void requireWorker(){
        if(SecurityContextHolder.getContext().getAuthentication()!=null || TenantEntryScope.isHttpRequest()
                || RequestContextHolder.getRequestAttributes()!=null)
            throw new AccessDeniedException("Event storage requires a trusted host worker");
    }
    private static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private void fill(PlatformEventEntity row,SecuritySignal s,String payload,String hash,Instant now,Instant expiry){
        row.setEventId(s.eventId().toString());row.setCorrelationId(s.correlationId().toString());row.setOccurredAt(s.occurredAt().truncatedTo(ChronoUnit.MILLIS));
        row.setReceivedAt(now);row.setExpiresAt(expiry);row.setFact(s.fact().name());row.setCategory(s.fact().category().name());
        row.setSeverity(s.fact().severity().name());row.setOutcome(s.fact().outcome().name());row.setAssessment(s.fact().assessment().name());
        row.setRuleVersion(s.fact().ruleVersion());row.setPayload(payload);row.setPayloadHash(hash);row.setHeld(false);
    }
    private void fill(TenantEventEntity row,SecuritySignal s,String payload,String hash,Instant now,Instant expiry){
        row.setEventId(s.eventId().toString());row.setCorrelationId(s.correlationId().toString());row.setOccurredAt(s.occurredAt().truncatedTo(ChronoUnit.MILLIS));
        row.setReceivedAt(now);row.setExpiresAt(expiry);row.setFact(s.fact().name());row.setCategory(s.fact().category().name());
        row.setSeverity(s.fact().severity().name());row.setOutcome(s.fact().outcome().name());row.setAssessment(s.fact().assessment().name());
        row.setRuleVersion(s.fact().ruleVersion());row.setPayload(payload);row.setPayloadHash(hash);row.setHeld(false);
    }
}
