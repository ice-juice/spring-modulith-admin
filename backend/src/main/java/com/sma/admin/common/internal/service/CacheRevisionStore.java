package com.sma.admin.common.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.internal.persistence.CacheRevisionEntity;
import com.sma.admin.common.internal.persistence.CacheRevisionMapper;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.cache.CachePolicy;
import java.time.Duration;
import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Common owns this coordination table. Business modules never access its mapper or rows. */
@Service
public class CacheRevisionStore {
    private final CacheRevisionMapper mapper;
    public CacheRevisionStore(CacheRevisionMapper mapper) { this.mapper = mapper; }

    public record State(long revision, long controlVersion, long statsEpoch, boolean paused, CachePolicy override) {
        public State {
            if(revision<0||controlVersion<0||statsEpoch<0)throw new IllegalArgumentException("Invalid cache authority state");
        }
        public static State initial() { return new State(0,0,0,false,null); }
    }
    public enum Change { POLICY, PAUSE, RESUME, RESET_STATS }

    /** Data and policy are read by one SELECT, not independent snapshots. */
    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED, readOnly=true, timeout=2)
    public State state(String region) {
        validate(region);
        return state(mapper.selectOne(new LambdaQueryWrapper<CacheRevisionEntity>().eq(CacheRevisionEntity::getRegion,region)));
    }

    /** Joins a governance write so its audit can capture the exact state protected by the same row lock. */
    @Transactional
    public State lockedState(String region) {
        validate(region);
        if(TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw new IllegalStateException("Cache control requires a writable transaction");
        mapper.ensureControlRow(IdWorker.getId(),CurrentUser.tenantId(),region,CurrentUser.userId());
        return state(mapper.selectOne(new LambdaQueryWrapper<CacheRevisionEntity>().eq(CacheRevisionEntity::getRegion,region).last("FOR UPDATE")));
    }

    /** Internal coordination only. The public governance facade must validate capabilities, permissions and deployment caps. */
    @Transactional
    public State change(String region,long expectedVersion,Change change,CachePolicy policy) {
        validate(region);Objects.requireNonNull(change);
        if(expectedVersion<0 || (change!=Change.POLICY && policy!=null))throw new IllegalArgumentException("Invalid cache control command");
        if(TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw new IllegalStateException("Cache control requires a writable transaction");
        if(policy!=null && (policy.ttl().getNano()%1_000_000!=0 || policy.negativeTtl().getNano()%1_000_000!=0))
            throw new IllegalArgumentException("Cache control duration precision is milliseconds");
        mapper.ensureControlRow(IdWorker.getId(),CurrentUser.tenantId(),region,CurrentUser.userId());
        var row=mapper.selectOne(new LambdaQueryWrapper<CacheRevisionEntity>().eq(CacheRevisionEntity::getRegion,region).last("FOR UPDATE"));
        if(row.getControlVersion()!=expectedVersion)throw new OptimisticLockingFailureException("Cache control version changed");
        row.setControlVersion(Math.addExact(row.getControlVersion(),1));
        if(change==Change.RESET_STATS)row.setStatsEpoch(Math.addExact(row.getStatsEpoch(),1));
        else {
            row.setRevision(Math.addExact(row.getRevision(),1));
            if(change==Change.PAUSE)row.setPaused(true);
            if(change==Change.RESUME)row.setPaused(false);
            if(change==Change.POLICY){
                row.setPolicyTtlMillis(policy==null?null:policy.ttl().toMillis());
                row.setPolicyNegativeTtlMillis(policy==null?null:policy.negativeTtl().toMillis());
                row.setPolicyMaxEntries(policy==null?null:policy.maxEntries());
                row.setPolicyMaxBytes(policy==null?null:policy.maxBytes());
                row.setPolicyMaxEntryBytes(policy==null?null:policy.maxEntryBytes());
            }
        }
        if(mapper.updateById(row)!=1)throw new IllegalStateException("Cache control update did not affect its owned row");
        return state(row);
    }

    private State state(CacheRevisionEntity row) {
        if(row==null)return State.initial();
        CachePolicy policy=row.getPolicyTtlMillis()==null?null:new CachePolicy(Duration.ofMillis(row.getPolicyTtlMillis()),
                Duration.ofMillis(row.getPolicyNegativeTtlMillis()),row.getPolicyMaxEntries(),row.getPolicyMaxBytes(),row.getPolicyMaxEntryBytes());
        return new State(row.getRevision(),row.getControlVersion(),row.getStatsEpoch(),row.getPaused(),policy);
    }

    @Transactional
    public long advance(String region) {
        validate(region);
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Cache invalidation must participate in a writable transaction");
        }
        mapper.advance(IdWorker.getId(), CurrentUser.tenantId(), region, CurrentUser.userId());
        return read(region);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED, readOnly=true, timeout=2)
    public long current(String region) { validate(region); return read(region); }

    private long read(String region) {
        var row = mapper.selectOne(new LambdaQueryWrapper<CacheRevisionEntity>().eq(CacheRevisionEntity::getRegion, region));
        return row == null ? 0 : row.getRevision();
    }
    private static void validate(String region) {
        if (region == null || region.length() > 96 || !region.matches("[a-z]+\\.[a-z][a-z0-9-]{0,79}")) throw new IllegalArgumentException("Invalid cache region");
    }
}
