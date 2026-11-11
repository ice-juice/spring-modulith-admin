package com.sma.admin.common.internal.service;

import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.cache.CacheNodeScope;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.security.*;

@Service
public class CacheConfirmationStore {
    private final CacheConfirmationMapper mapper;
    private final CacheNodeLeaseStore nodes;
    private final JsonMapper json=JsonMapper.builder().build();
    public CacheConfirmationStore(CacheConfirmationMapper mapper,CacheNodeLeaseStore nodes){this.mapper=mapper;this.nodes=nodes;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void publish(CacheTaskEntity task,CacheTaskItemEntity item){
        if(task.getTenantId()!=CurrentUser.tenantId()||item.getTenantId()!=CurrentUser.tenantId()||task.getCreatedBy()!=CurrentUser.require().userId()
                ||!Objects.equals(task.getId(),item.getTaskId())||!"APPLIED".equals(item.getState())
                ||item.getDataRevision()==null||item.getControlVersion()==null)throw new IllegalArgumentException("Invalid cache confirmation publication");
        if(task.getNodeScopeJson()==null)return;
        var scope=json.readValue(task.getNodeScopeJson(),CacheNodeScope.class);
        if(scope.members().isEmpty()||scope.members().size()>32)throw new IllegalArgumentException("Invalid frozen node coverage");
        for(var member:scope.members()){
            var row=new CacheConfirmationEntity();row.setTaskId(task.getId());row.setRegion(item.getRegion());row.setNodeId(member.nodeId());
            row.setExpectedProcess(member.processId());row.setExpectedEpoch(member.epoch());row.setManifestHash(scope.manifestHash());
            row.setRequiredRevision(item.getDataRevision());row.setRequiredControl(item.getControlVersion());
            row.setState(member.processId()==null?"UNKNOWN":"WAITING");row.setLastError("");row.setNextAttemptAt(Instant.EPOCH);mapper.insert(row);
        }
    }
    public record Request(long id,String region,long requiredRevision,long requiredControl) {}
    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public List<Request> due(CacheNodeIdentity identity,long epoch,int limit){
        requireJob(identity);if(limit<1||limit>16)throw new IllegalArgumentException("Invalid confirmation page");
        return mapper.selectList(new LambdaQueryWrapper<CacheConfirmationEntity>().eq(CacheConfirmationEntity::getNodeId,identity.properties().id())
                .eq(CacheConfirmationEntity::getExpectedProcess,identity.processId()).eq(CacheConfirmationEntity::getExpectedEpoch,epoch)
                .eq(CacheConfirmationEntity::getManifestHash,identity.manifestHash()).eq(CacheConfirmationEntity::getState,"WAITING")
                .le(CacheConfirmationEntity::getNextAttemptAt,mapper.now()).orderByAsc(CacheConfirmationEntity::getNextAttemptAt).orderByAsc(CacheConfirmationEntity::getId).last("LIMIT "+limit))
                .stream().map(row->new Request(row.getId(),row.getRegion(),row.getRequiredRevision(),row.getRequiredControl())).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED,timeout=5)
    public boolean confirm(long id,CacheNodeIdentity identity,long epoch,CacheRevisionStore.State applied){
        requireJob(identity);if(!nodes.holdsLease(identity,epoch))return false;
        var row=owned(id,identity,epoch);if(row==null||!row.getState().equals("WAITING"))return false;
        if(applied.revision()<row.getRequiredRevision()||applied.controlVersion()<row.getRequiredControl())return false;
        var now=mapper.now();row.setObservedRevision(applied.revision());row.setObservedControl(applied.controlVersion());
        row.setState("CONFIRMED");row.setConfirmedAt(now);row.setLastAttemptAt(now);row.setLastError("");mapper.updateById(row);return true;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED,timeout=5)
    public void failed(long id,CacheNodeIdentity identity,long epoch){
        requireJob(identity);if(!nodes.holdsLease(identity,epoch))return;
        var row=owned(id,identity,epoch);if(row==null||!row.getState().equals("WAITING"))return;
        var now=mapper.now();row.setLastAttemptAt(now);row.setLastError("APPLY_FAILED");row.setNextAttemptAt(now.plusSeconds(5));mapper.updateById(row);
    }
    private CacheConfirmationEntity owned(long id,CacheNodeIdentity identity,long epoch){
        return mapper.selectOne(new LambdaQueryWrapper<CacheConfirmationEntity>().eq(CacheConfirmationEntity::getId,id)
                .eq(CacheConfirmationEntity::getNodeId,identity.properties().id()).eq(CacheConfirmationEntity::getExpectedProcess,identity.processId())
                .eq(CacheConfirmationEntity::getExpectedEpoch,epoch).eq(CacheConfirmationEntity::getManifestHash,identity.manifestHash()).last("FOR UPDATE"));
    }
    private void requireJob(CacheNodeIdentity identity){var actor=ActorContext.require();
        if(actor.kind()!=ActorRef.Kind.SYSTEM_JOB||!actor.subject().equals("cache-node:"+identity.processId()))throw new AccessDeniedException("Node confirmation job required");}
}
