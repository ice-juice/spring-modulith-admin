package com.sma.admin.common.internal.service;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.cache.CacheTaskDispatchProperties;
import com.sma.admin.common.internal.cache.CacheTaskPlanner;
import com.sma.admin.common.internal.cache.CacheWarmupRegistry;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.cache.CacheResourceRegistry;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.security.*;

/** Native work protocol: atomic DB control changes and bounded read-only warmup checkpoints. No external exactly-once promise. */
@Service
public class CacheTaskExecutor {
    private final CacheTaskMapper tasks;
    private final CacheTaskItemMapper items;
    private final CacheRevisionStore revisions;
    private final CacheTaskPlanner planner;
    private final ManagedCache cache;
    private final CacheGovernance governance;
    private final ObjectProvider<CacheTaskActorResolver> actors;
    private final TenantEntryQuery tenants;
    private final CacheTaskDispatchProperties properties;
    private final CacheConfirmationStore confirmations;
    private final CacheWarmupRegistry warmups;
    private final CacheNodeIdentity identity;
    private final CacheResourceRegistry resources;
    private final TransactionTemplate writes;
    private final JsonMapper json=JsonMapper.builder().build();
    public CacheTaskExecutor(CacheTaskMapper tasks,CacheTaskItemMapper items,CacheRevisionStore revisions,CacheTaskPlanner planner,
            ManagedCache cache,CacheGovernance governance,ObjectProvider<CacheTaskActorResolver> actors,TenantEntryQuery tenants,
            CacheTaskDispatchProperties properties,PlatformTransactionManager transactions,CacheConfirmationStore confirmations){
        this(tasks,items,revisions,planner,cache,governance,actors,tenants,properties,transactions,confirmations,null,null);
    }
    public CacheTaskExecutor(CacheTaskMapper tasks,CacheTaskItemMapper items,CacheRevisionStore revisions,CacheTaskPlanner planner,
            ManagedCache cache,CacheGovernance governance,ObjectProvider<CacheTaskActorResolver> actors,TenantEntryQuery tenants,
            CacheTaskDispatchProperties properties,PlatformTransactionManager transactions,CacheConfirmationStore confirmations,CacheWarmupRegistry warmups,CacheNodeIdentity identity){
        this(tasks,items,revisions,planner,cache,governance,actors,tenants,properties,transactions,confirmations,warmups,identity,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CacheTaskExecutor(CacheTaskMapper tasks,CacheTaskItemMapper items,CacheRevisionStore revisions,CacheTaskPlanner planner,
            ManagedCache cache,CacheGovernance governance,ObjectProvider<CacheTaskActorResolver> actors,TenantEntryQuery tenants,
            CacheTaskDispatchProperties properties,PlatformTransactionManager transactions,CacheConfirmationStore confirmations,CacheWarmupRegistry warmups,CacheNodeIdentity identity,CacheResourceRegistry resources){
        this.tasks=tasks;this.items=items;this.revisions=revisions;this.planner=planner;this.cache=cache;this.governance=governance;
        this.actors=actors;this.tenants=tenants;this.properties=properties;
        this.confirmations=confirmations;
        this.warmups=warmups;this.identity=identity;
        this.resources=resources;
        writes=new TransactionTemplate(transactions);writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);writes.setTimeout(10);
    }
    public record Lease(String taskId,long fence,String owner) {}
    public Optional<Lease> claim(String owner){
        requireJob("cache-dispatch");requireNoTransaction();
        if(owner==null||!owner.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid cache worker identity");
        return writes.execute(status->{
            var now=tasks.now();
            var candidates=tasks.selectList(new LambdaQueryWrapper<CacheTaskEntity>().in(CacheTaskEntity::getState,"QUEUED","RUNNING")
                    .and(ready->ready.eq(CacheTaskEntity::getCancelRequested,true)
                        .or().notExists("SELECT 1 FROM common_cache_task_item ci WHERE ci.tenant_id=common_cache_task.tenant_id AND ci.task_id=common_cache_task.id AND ci.state='PENDING'")
                        .or().exists("SELECT 1 FROM common_cache_task_item ci WHERE ci.tenant_id=common_cache_task.tenant_id AND ci.task_id=common_cache_task.id AND ci.state='PENDING' AND ci.ordinal=(SELECT MIN(first_item.ordinal) FROM common_cache_task_item first_item WHERE first_item.tenant_id=ci.tenant_id AND first_item.task_id=ci.task_id AND first_item.state='PENDING') AND (ci.native_process='' OR ci.native_process={0} OR ci.native_started_at IS NOT NULL OR ci.native_deadline<={1})",owner,now))
                    .le(CacheTaskEntity::getLeaseUntil,now).orderByAsc(CacheTaskEntity::getId).last("LIMIT 16 FOR UPDATE SKIP LOCKED"));
            for(var row:candidates){
            row.setState("RUNNING");row.setFence(Math.addExact(row.getFence(),1));row.setLeaseOwner(owner);row.setLeaseUntil(now.plus(properties.lease()));
            if(row.getCancelRequested()){cancelPending(row);finish(row);return Optional.empty();}
            var pending=pending(row.getId());
            if(pending==null){finish(row);return Optional.empty();}
            if(pending.getNativeStartedAt()!=null){pending.setState("UNKNOWN");pending.setFailureCode("NATIVE_RESULT_UNKNOWN");items.updateById(pending);finish(row);return Optional.empty();}
            if(!pending.getNativeProcess().isEmpty()){
                if(!pending.getNativeDeadline().isAfter(now)){pending.setState("FAILED");pending.setFailureCode("TARGET_NODE_UNAVAILABLE");items.updateById(pending);finish(row);return Optional.empty();}
                if(!pending.getNativeProcess().equals(owner))continue; // No persistent lease/fence change on a foreign process.
            }
            if(pending.getAttempts()>=3){pending.setState("FAILED");pending.setFailureCode("RETRY_EXHAUSTED");items.updateById(pending);finish(row);return Optional.empty();}
            pending.setAttempts(pending.getAttempts()+1);items.updateById(pending);
            tasks.updateById(row);return Optional.of(new Lease(row.getPublicId(),row.getFence(),owner));
            }
            return Optional.empty();
        });
    }
    public boolean executeOne(Lease lease){
        requireJob("cache-task:"+lease.taskId());requireNoTransaction();
        try{
            var row=tasks.selectOne(new LambdaQueryWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,lease.taskId()));
            if(row!=null&&row.getAction().equals("PREWARM"))return executeWarmup(lease);
            var next=row==null?null:pending(row.getId());
            if(next!=null&&!next.getNativeProcess().isEmpty())return executeNative(lease);
            return Boolean.TRUE.equals(writes.execute(status->executeLocked(lease)));
        }
        catch(AccessDeniedException denied){return finishFailure(lease,"DENIED","AUTHORIZATION_REVOKED");}
        catch(CacheTaskRejected|CacheControlConflictException changed){return finishFailure(lease,"CHANGED","TARGET_CHANGED");}
        catch(WarmupRejected rejected){return finishFailure(lease,"FAILED",rejected.code);}
        // Infrastructure/commit uncertainty leaves the lease. The next claim rereads persisted results before any retry.
    }
    private boolean executeLocked(Lease lease){
        var row=locked(lease);if(row==null)return false;
        if(row.getCancelRequested()){cancelPending(row);finish(row);return true;}
        var item=pending(row.getId());if(item==null){finish(row);return true;}
        var frozen=json.readValue(row.getFrozenJson(),CacheTaskStore.Frozen.class);
        var target=frozen.targets().stream().filter(candidate->candidate.region().equals(item.getRegion())).findFirst().orElseThrow(()->new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED));
        var principal=resolveActor(row);
        asActor(principal,()->{
            var action=CacheTasks.Action.valueOf(row.getAction());planner.authorize(action,List.of(target));planner.validateFrozen(List.of(target));
            if(action==CacheTasks.Action.CLEAR)cache.invalidate(target.region());
            else governance.change(target.region(),new CacheGovernance.Command(switch(action){
                case PAUSE->CacheGovernance.Action.PAUSE;case RESUME->CacheGovernance.Action.RESUME;case RESET_STATS->CacheGovernance.Action.RESET_STATS;
                default->throw new IllegalStateException("Unsupported task operation");
            },target.controlVersion(),null,row.getReason()));
            var applied=revisions.lockedState(target.region());item.setDataRevision(applied.revision());item.setControlVersion(applied.controlVersion());
            item.setState("APPLIED");item.setFailureCode("");items.updateById(item);confirmations.publish(row,item);return null;
        });
        finish(row);return true;
    }
    private SmaPrincipal resolveActor(CacheTaskEntity row){
        var state=tenants.securityState(row.getTenantId()).orElseThrow(()->new AccessDeniedException("Tenant unavailable"));
        if(!state.acceptsBusiness()||state.generation()!=row.getTenantGeneration())throw new AccessDeniedException("Tenant generation changed");
        var resolver=actors.getIfUnique();if(resolver==null)throw new IllegalStateException("Cache task actor resolver unavailable");
        var principal=resolver.resolve(row.getCreatedBy(),row.getPortal()).orElseThrow(()->new AccessDeniedException("Account unavailable"));
        if(principal.tenantId()!=row.getTenantId()||principal.userId()!=row.getCreatedBy()||!principal.portal().equals(row.getPortal())||principal.machine())throw new AccessDeniedException("Task identity mismatch");
        return principal;
    }
    private record WarmBatch(long itemId,CacheTasks.Target target,String cursor,long revision,int limit) {}
    private record WarmGuard(CacheTaskEntity task,CacheTaskItemEntity item,SmaPrincipal actor) {}
    private static final class WarmupRejected extends RuntimeException {
        private final String code;
        WarmupRejected(String code){super(code);this.code=code;}
    }
    private boolean executeWarmup(Lease lease){
        if(warmups==null||identity==null)throw new WarmupRejected("WARMUP_UNAVAILABLE");
        var batch=writes.execute(status->{
            var row=locked(lease);if(row==null)return null;
            if(row.getCancelRequested()){cancelPending(row);finish(row);return null;}
            var item=pending(row.getId());if(item==null){finish(row);return null;}
            var frozen=json.readValue(row.getFrozenJson(),CacheTaskStore.Frozen.class);
            var target=frozen.targets().stream().filter(candidate->candidate.region().equals(item.getRegion())).findFirst()
                    .orElseThrow(()->new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED));
            var actor=resolveActor(row);
            return asActor(actor,()->{
                planner.authorize(CacheTasks.Action.PREWARM,List.of(target));planner.validateFrozen(List.of(target));
                if(item.getWarmStartedAt()!=null&&!identity.processId().equals(item.getWarmProcess()))throw new WarmupRejected("EXECUTION_PROCESS_CHANGED");
                var now=tasks.now();
                if(row.getWarmStartedAt()==null){row.setWarmStartedAt(now);tasks.updateById(row);}
                if(item.getWarmStartedAt()==null){item.setWarmStartedAt(now);item.setWarmProcess(identity.processId());items.updateById(item);}
                checkWarmBudget(row,item,target,now);
                long remaining=Math.min(target.warmup().limits().maxItems()-item.getWarmReads(),1000L-totalWarmReads(row));
                if(remaining<=0)throw new WarmupRejected("ITEM_LIMIT");
                return new WarmBatch(item.getId(),target,item.getWarmCursor(),revisions.lockedState(item.getRegion()).revision(),(int)Math.min(remaining,target.warmup().limits().pageSize()));
            });
        });
        if(batch==null)return false;
        var loader=warmups.require(batch.target().region());
        var authority=writes.execute(status->guardWarm(lease,batch));if(authority==null)return true;
        var page=asActor(authority.actor(),()->loader.enumerate(batch.cursor(),batch.limit()));
        if(page==null||page.keys().size()>batch.limit()||(!page.complete()&&page.nextCursor().equals(batch.cursor())))throw new WarmupRejected("LOADER_INVALID");
        for(var key:page.keys()){
            var current=writes.execute(status->guardWarm(lease,batch));if(current==null)return true;
            // Absolutely no row lock or active DB transaction spans loading, Redis access or module I/O.
            asActor(current.actor(),()->{loader.load(key);return null;});
        }
        return Boolean.TRUE.equals(writes.execute(status->{
            var current=guardWarm(lease,batch);if(current==null)return false;
            var row=current.task();var item=current.item();
            item.setWarmCursor(page.nextCursor());item.setWarmReads(Math.addExact(item.getWarmReads(),page.keys().size()));item.setAttempts(0);
            item.setDataRevision(batch.revision());item.setControlVersion(batch.target().controlVersion());
            if(page.complete())item.setState("APPLIED");
            else if(item.getWarmReads()>=batch.target().warmup().limits().maxItems()||totalWarmReads(row)+page.keys().size()>=1000){item.setState("FAILED");item.setFailureCode("ITEM_LIMIT");}
            items.updateById(item);finish(row);
            // A page-sized burst with a post-page cooldown bounds the average rate, without sleeping a worker.
            if(row.getState().equals("RUNNING")){
                long delay=(page.keys().size()*1000L+batch.target().warmup().limits().itemsPerSecond()-1)/batch.target().warmup().limits().itemsPerSecond();
                row.setLeaseUntil(tasks.now().plusMillis(Math.max(1,delay)));tasks.updateById(row);
            }
            return true;
        }));
    }
    private WarmGuard guardWarm(Lease lease,WarmBatch batch){
        var row=locked(lease);if(row==null)return null;
        if(row.getCancelRequested()){cancelPending(row);finish(row);return null;}
        var item=pending(row.getId());
        if(item==null||item.getId()!=batch.itemId()||!item.getWarmCursor().equals(batch.cursor()))return null;
        if(!identity.processId().equals(item.getWarmProcess()))throw new WarmupRejected("EXECUTION_PROCESS_CHANGED");
        var actor=resolveActor(row);
        asActor(actor,()->{
            planner.authorize(CacheTasks.Action.PREWARM,List.of(batch.target()));planner.validateFrozen(List.of(batch.target()));
            if(revisions.lockedState(item.getRegion()).revision()!=batch.revision())throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
            checkWarmBudget(row,item,batch.target(),tasks.now());return null;
        });
        return new WarmGuard(row,item,actor);
    }
    private long totalWarmReads(CacheTaskEntity task){return all(task.getId()).stream().mapToLong(CacheTaskItemEntity::getWarmReads).sum();}
    private void checkWarmBudget(CacheTaskEntity row,CacheTaskItemEntity item,CacheTasks.Target target,Instant now){
        if(!row.getWarmStartedAt().plusSeconds(120).isAfter(now)||!item.getWarmStartedAt().plus(target.warmup().limits().maxDuration()).isAfter(now))throw new WarmupRejected("TIME_LIMIT");
    }
    private record NativeWork(long itemId,CacheTasks.Target target,CacheDescriptor.Operation operation,SmaPrincipal actor) {}
    private boolean executeNative(Lease lease){
        if(resources==null||identity==null)throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        var work=writes.execute(status->{
            var row=locked(lease);if(row==null)return null;
            if(row.getCancelRequested()){cancelPending(row);finish(row);return null;}
            var item=pending(row.getId());if(item==null||item.getNativeStartedAt()!=null)return null;
            var frozen=json.readValue(row.getFrozenJson(),CacheTaskStore.Frozen.class);
            var target=frozen.targets().stream().filter(candidate->candidate.region().equals(item.getRegion())).findFirst().orElseThrow(()->new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED));
            if(target.resource()==null||!target.resource().processId().equals(item.getNativeProcess())||!item.getNativeProcess().equals(identity.processId())
                    ||!item.getNativeProcess().equals(lease.owner())||!item.getNativeDeadline().isAfter(tasks.now()))throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
            var actor=resolveActor(row);var action=CacheTasks.Action.valueOf(row.getAction());
            asActor(actor,()->{planner.authorize(action,List.of(target));planner.validateFrozen(List.of(target));return null;});
            item.setNativeStartedAt(tasks.now());items.updateById(item);
            return new NativeWork(item.getId(),target,CacheTaskPlanner.operation(action),actor);
        });
        if(work==null)return false;
        final CacheResourceAdapter.Outcome result;
        try{result=asActor(work.actor(),()->resources.execute(work.target().region(),work.operation(),work.target().resource()));}
        catch(RuntimeException uncertain){return finishNativeUnknown(lease,work.itemId());}
        // A commit failure here is uncertain. Never call the adapter again to obtain another result.
        return Boolean.TRUE.equals(writes.execute(status->{
            var row=locked(lease);if(row==null)return false;
            var item=pending(row.getId());if(item==null||item.getId()!=work.itemId()||item.getNativeStartedAt()==null)return false;
            item.setState("APPLIED");item.setFailureCode("");item.setNativeCompletedAt(tasks.now());item.setNativeAffected(result.affectedEntries());item.setNativeRemaining(result.remainingEntries());
            items.updateById(item);finish(row);return true;
        }));
    }
    private boolean finishNativeUnknown(Lease lease,long itemId){
        return Boolean.TRUE.equals(writes.execute(status->{
            var row=locked(lease);if(row==null)return false;
            var item=pending(row.getId());if(item==null||item.getId()!=itemId||item.getNativeStartedAt()==null)return false;
            item.setState("UNKNOWN");item.setFailureCode("NATIVE_RESULT_UNKNOWN");items.updateById(item);finish(row);return true;
        }));
    }
    private boolean finishFailure(Lease lease,String state,String code){
        // This is a fresh transaction after the failed effect transaction has rolled back.
        return Boolean.TRUE.equals(writes.execute(status->{
            var row=locked(lease);if(row==null)return false;
            if(row.getCancelRequested())cancelPending(row);
            else {var item=pending(row.getId());if(item!=null){item.setState(state);item.setFailureCode(code);items.updateById(item);}}
            finish(row);return true;
        }));
    }
    private CacheTaskEntity locked(Lease lease){
        var row=tasks.selectOne(new LambdaQueryWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,lease.taskId()).last("FOR UPDATE"));
        if(row==null||!row.getState().equals("RUNNING")||row.getFence()!=lease.fence()||!row.getLeaseOwner().equals(lease.owner())||!row.getLeaseUntil().isAfter(tasks.now()))return null;
        return row;
    }
    private CacheTaskItemEntity pending(long taskId){return items.selectOne(new LambdaQueryWrapper<CacheTaskItemEntity>().eq(CacheTaskItemEntity::getTaskId,taskId)
            .eq(CacheTaskItemEntity::getState,"PENDING").orderByAsc(CacheTaskItemEntity::getOrdinal).last("LIMIT 1"));}
    private List<CacheTaskItemEntity> all(long taskId){return items.selectList(new LambdaQueryWrapper<CacheTaskItemEntity>().eq(CacheTaskItemEntity::getTaskId,taskId).orderByAsc(CacheTaskItemEntity::getOrdinal));}
    private void cancelPending(CacheTaskEntity row){for(var item:all(row.getId()))if(item.getState().equals("PENDING")){
        if(item.getNativeStartedAt()!=null){item.setState("UNKNOWN");item.setFailureCode("NATIVE_RESULT_UNKNOWN");}else item.setState("CANCELLED");items.updateById(item);
    }}
    private void finish(CacheTaskEntity row){
        var entries=all(row.getId());
        if(entries.isEmpty())row.setState("FAILED");
        else if(entries.stream().anyMatch(item->item.getState().equals("PENDING")))row.setState("RUNNING");
        else if(entries.stream().allMatch(item->item.getState().equals("APPLIED")))row.setState("SUCCEEDED");
        else if(entries.stream().anyMatch(item->item.getState().equals("APPLIED")))row.setState("PARTIAL");
        else if(entries.stream().anyMatch(item->item.getState().equals("UNKNOWN")))row.setState("UNKNOWN");
        else if(entries.stream().allMatch(item->item.getState().equals("CANCELLED")))row.setState("CANCELLED");
        else row.setState("FAILED");
        row.setLeaseOwner("");row.setLeaseUntil(Instant.EPOCH);tasks.updateById(row);
    }
    private <T> T asActor(SmaPrincipal principal,Supplier<T> work){
        var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(context);try{return work.get();}finally{SecurityContextHolder.setContext(previous);}
    }
    private void requireJob(String work){var actor=ActorContext.require();if(actor.kind()!=ActorRef.Kind.SYSTEM_JOB||!actor.subject().equals(work))throw new AccessDeniedException("Persisted cache work required");}
    private void requireNoTransaction(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Cache worker must own its transaction");}
}
