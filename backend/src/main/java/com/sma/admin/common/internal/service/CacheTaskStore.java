package com.sma.admin.common.internal.service;

import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.cache.CacheTaskProperties;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.security.CurrentUser;

/** Owns persistent work; only bounded, never-accepted previews may be physically retired. */
@Service
public class CacheTaskStore {
    private final CacheTaskMapper tasks;
    private final CacheTaskItemMapper items;
    private final CacheTaskGateMapper gates;
    private final CacheTaskProperties properties;
    private final CacheNodeIdentity identity;
    private final CacheNodeLeaseStore nodes;
    private final JsonMapper json=JsonMapper.builder().build();
    public CacheTaskStore(CacheTaskMapper tasks,CacheTaskItemMapper items,CacheTaskGateMapper gates,CacheTaskProperties properties,CacheNodeIdentity identity,CacheNodeLeaseStore nodes){
        this.tasks=tasks;this.items=items;this.gates=gates;this.properties=properties;this.identity=identity;this.nodes=nodes;
    }
    public record Frozen(List<CacheTasks.Target> targets,List<CacheTasks.Skipped> skipped){
        public Frozen {targets=List.copyOf(targets);skipped=List.copyOf(skipped);}
    }
    @Transactional(timeout=10)
    public CacheTasks.View create(CacheTasks.Action action,String reason,Frozen frozen,long tenantGeneration){
        if((long)frozen.targets().size()*identity.properties().expected().size()>512)throw new CacheTaskRejected(CacheTaskRejected.Reason.LIMIT);
        long tenant=CurrentUser.tenantId();
        gates.ensure(IdWorker.getId(),tenant);
        gates.selectOne(new LambdaQueryWrapper<CacheTaskGateEntity>().last("FOR UPDATE"));
        tasks.purgeExpiredPreviews(tenant);
        if(tasks.selectCount(new LambdaQueryWrapper<>())>=properties.maxRecordsPerTenant())throw new CacheTaskRejected(CacheTaskRejected.Reason.LIMIT);
        var row=new CacheTaskEntity();row.setPublicId(UUID.randomUUID().toString());row.setPortal(CurrentUser.portal());row.setTenantGeneration(tenantGeneration);
        row.setAction(action.name());row.setState("PREVIEW");row.setReason(reason);row.setFrozenJson(json.writeValueAsString(frozen));
        row.setNodeScopeJson(json.writeValueAsString(nodes.snapshot(identity)));
        row.setExpiresAt(tasks.now().plus(properties.previewTtl()));row.setCancelRequested(false);row.setFence(0L);
        row.setLeaseOwner("");row.setLeaseUntil(Instant.EPOCH);tasks.insert(row);return view(tasks.selectById(row.getId()));
    }
    @Transactional(readOnly=true)
    public CacheTasks.View get(String id){return view(owned(id,false));}
    @Transactional(readOnly=true)
    public CacheNodeScope nodeScope(String id){var row=owned(id,false);return row.getNodeScopeJson()==null?null:json.readValue(row.getNodeScopeJson(),CacheNodeScope.class);}
    @Transactional(readOnly=true)
    public List<CacheTasks.View> recent(int limit){
        if(limit<1||limit>100)throw new BizException(CommonErrorCode.BAD_REQUEST);
        return tasks.selectList(ownerQuery().orderByDesc(CacheTaskEntity::getCreatedAt).orderByDesc(CacheTaskEntity::getId).last("LIMIT "+limit))
                .stream().map(this::view).toList();
    }
    @Transactional(propagation=Propagation.MANDATORY,timeout=10)
    public CacheTasks.View enqueue(String id,long tenantGeneration,Runnable validate){
        var row=owned(id,true);
        if(!row.getState().equals("PREVIEW")||row.getTenantGeneration()!=tenantGeneration)throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        if(!row.getExpiresAt().isAfter(tasks.now()))throw new CacheTaskRejected(CacheTaskRejected.Reason.EXPIRED);
        var frozen=frozen(row);if(frozen.targets().isEmpty())throw new CacheTaskRejected(CacheTaskRejected.Reason.EMPTY);
        nodes.validateFrozen(identity,row.getNodeScopeJson()==null?null:json.readValue(row.getNodeScopeJson(),CacheNodeScope.class));
        validate.run();
        for(int i=0;i<frozen.targets().size();i++){
            var item=new CacheTaskItemEntity();item.setTaskId(row.getId());item.setOrdinal(i);item.setRegion(frozen.targets().get(i).region());
            item.setState("PENDING");item.setFailureCode("");item.setAttempts(0);item.setWarmCursor("");item.setWarmReads(0L);item.setWarmProcess("");
            var resource=frozen.targets().get(i).resource();item.setNativeProcess(resource==null?"":resource.processId());
            if(resource!=null)item.setNativeDeadline(tasks.now().plusSeconds(300));items.insert(item);
        }
        row.setState("QUEUED");tasks.updateById(row);return view(row);
    }
    @Transactional(timeout=10)
    public CacheTasks.View cancel(String id){
        var row=owned(id,true);
        if(Set.of("PREVIEW","QUEUED").contains(row.getState())){
            row.setCancelRequested(true);row.setState("CANCELLED");
            for(var item:items(row.getId()))if(item.getState().equals("PENDING")){item.setState("CANCELLED");items.updateById(item);}
            tasks.updateById(row);
        }else if(row.getState().equals("RUNNING")&&!row.getCancelRequested()){
            row.setCancelRequested(true);tasks.updateById(row);
        }
        return view(row);
    }
    private LambdaQueryWrapper<CacheTaskEntity> ownerQuery(){
        return new LambdaQueryWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getCreatedBy,CurrentUser.require().userId()).eq(CacheTaskEntity::getPortal,CurrentUser.portal());
    }
    private CacheTaskEntity owned(String id,boolean lock){
        if(id==null||!id.matches("[0-9a-f-]{36}"))throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        var query=ownerQuery().eq(CacheTaskEntity::getPublicId,id);if(lock)query.last("FOR UPDATE");
        var row=tasks.selectOne(query);if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row;
    }
    private Frozen frozen(CacheTaskEntity row){return json.readValue(row.getFrozenJson(),Frozen.class);}
    private List<CacheTaskItemEntity> items(long id){return items.selectList(new LambdaQueryWrapper<CacheTaskItemEntity>().eq(CacheTaskItemEntity::getTaskId,id).orderByAsc(CacheTaskItemEntity::getOrdinal));}
    private CacheTasks.View view(CacheTaskEntity row){
        var frozen=frozen(row);
        return new CacheTasks.View(row.getPublicId(),CacheTasks.Action.valueOf(row.getAction()),CacheTasks.State.valueOf(row.getState()),row.getReason(),
                row.getCreatedAt(),row.getExpiresAt(),row.getCancelRequested(),frozen.targets(),frozen.skipped(),
                items(row.getId()).stream().map(item->new CacheTasks.Item(item.getRegion(),item.getState(),item.getDataRevision(),item.getControlVersion(),item.getFailureCode(),
                        row.getAction().equals("PREWARM")?new CacheTasks.WarmupProgress(item.getWarmReads(),item.getWarmProcess(),item.getWarmStartedAt()):null,
                        item.getNativeProcess().isEmpty()?null:new CacheTasks.ResourceProgress(item.getNativeProcess(),item.getNativeStartedAt(),item.getNativeCompletedAt(),item.getNativeAffected(),item.getNativeRemaining(),item.getNativeDeadline()))).toList());
    }
}
