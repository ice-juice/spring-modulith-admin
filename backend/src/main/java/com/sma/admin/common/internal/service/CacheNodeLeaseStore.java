package com.sma.admin.common.internal.service;

import java.time.Duration;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.security.*;

/** The only writer of global membership. This is host framework metadata, not a tenant bypass API. */
@Service
public class CacheNodeLeaseStore {
    private final CacheNodeGroupMapper groups;
    private final CacheNodeLeaseMapper leases;
    public CacheNodeLeaseStore(CacheNodeGroupMapper groups,CacheNodeLeaseMapper leases){this.groups=groups;this.leases=leases;}
    public record Lease(String nodeId,String processId,long epoch,java.time.Instant until) {}
    @Transactional(isolation=Isolation.READ_COMMITTED,timeout=5)
    public Lease heartbeat(CacheNodeIdentity identity){
        if(SecurityContextHolder.getContext().getAuthentication()!=null)throw new AccessDeniedException("Host heartbeat only");
        var cluster=identity.clusterHash();var manifest=identity.manifestHash();
        var groupQuery=new LambdaQueryWrapper<CacheNodeGroupEntity>().eq(CacheNodeGroupEntity::getClusterHash,cluster);
        var peekGroup=groups.selectOne(groupQuery);
        var peekLease=leases.selectOne(new LambdaQueryWrapper<CacheNodeLeaseEntity>().eq(CacheNodeLeaseEntity::getClusterHash,cluster)
                .eq(CacheNodeLeaseEntity::getNodeId,identity.properties().id()));
        boolean renewal=peekGroup!=null&&peekGroup.getManifestHash().equals(manifest)&&peekGroup.getManifestVersion().longValue()==identity.properties().version()
                &&peekLease!=null&&peekLease.getProcessId().equals(identity.processId())&&peekLease.getManifestHash().equals(manifest);
        // Normal renewal is compatible with a tenant's submission lock. Only membership mutations need exclusivity.
        if(!renewal)groups.ensure(IdWorker.getId(),cluster,manifest,identity.properties().version());
        var group=groups.selectOne(new LambdaQueryWrapper<CacheNodeGroupEntity>().eq(CacheNodeGroupEntity::getClusterHash,cluster)
                .last(renewal?"FOR SHARE":"FOR UPDATE"));
        if(renewal&&(!group.getManifestHash().equals(manifest)||group.getManifestVersion().longValue()!=identity.properties().version()))
            throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        var now=groups.now();
        if(group.getManifestVersion()>identity.properties().version())throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        if(!group.getManifestHash().equals(manifest)){
            if(identity.properties().version()<=group.getManifestVersion())throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
            if(leases.selectCount(new LambdaQueryWrapper<CacheNodeLeaseEntity>().eq(CacheNodeLeaseEntity::getClusterHash,cluster).gt(CacheNodeLeaseEntity::getLeaseUntil,now))>0)
                throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
            group.setManifestHash(manifest);group.setManifestVersion(identity.properties().version());groups.updateById(group);
        }
        var row=leases.selectOne(new LambdaQueryWrapper<CacheNodeLeaseEntity>().eq(CacheNodeLeaseEntity::getClusterHash,cluster)
                .eq(CacheNodeLeaseEntity::getNodeId,identity.properties().id()).last("FOR UPDATE"));
        if(renewal&&(row==null||!row.getProcessId().equals(identity.processId())||!row.getManifestHash().equals(manifest)))
            throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        if(row!=null&&!row.getProcessId().equals(identity.processId())&&row.getLeaseUntil().isAfter(now))
            throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        if(row==null){
            // Configuration is operator-owned, but even stale logical slots have a hard deployment budget.
            if(leases.selectCount(new LambdaQueryWrapper<CacheNodeLeaseEntity>().eq(CacheNodeLeaseEntity::getClusterHash,cluster))>=128)
                throw new CacheTaskRejected(CacheTaskRejected.Reason.LIMIT);
            row=new CacheNodeLeaseEntity();row.setClusterHash(cluster);row.setNodeId(identity.properties().id());row.setEpoch(1L);
        }else if(!row.getProcessId().equals(identity.processId())||!row.getManifestHash().equals(manifest))row.setEpoch(Math.addExact(row.getEpoch(),1));
        row.setProcessId(identity.processId());row.setManifestHash(manifest);row.setLeaseUntil(now.plus(Duration.ofSeconds(30)));
        if(row.getId()==null)leases.insert(row);else leases.updateById(row);
        return new Lease(row.getNodeId(),row.getProcessId(),row.getEpoch(),row.getLeaseUntil());
    }
    @Transactional(readOnly=true)
    public CacheNodeScope snapshot(CacheNodeIdentity identity){
        var now=groups.now();var group=groups.selectOne(new LambdaQueryWrapper<CacheNodeGroupEntity>().eq(CacheNodeGroupEntity::getClusterHash,identity.clusterHash()));
        boolean incompatible=group!=null&&(!group.getManifestHash().equals(identity.manifestHash())||group.getManifestVersion().longValue()!=identity.properties().version());
        var stored=leases.selectList(new LambdaQueryWrapper<CacheNodeLeaseEntity>().eq(CacheNodeLeaseEntity::getClusterHash,identity.clusterHash())
                .in(CacheNodeLeaseEntity::getNodeId,identity.properties().expected()));
        var indexed=new HashMap<String,CacheNodeLeaseEntity>();stored.forEach(row->indexed.put(row.getNodeId(),row));
        var members=identity.properties().expected().stream().map(node->{
            var row=indexed.get(node);
            if(row==null)return new CacheNodeScope.Member(node,null,null,null,incompatible?"CONFIGURATION_MISMATCH":"UNKNOWN");
            String state=incompatible||!row.getManifestHash().equals(identity.manifestHash())?"CONFIGURATION_MISMATCH":row.getLeaseUntil().isAfter(now)?"ONLINE":"OFFLINE";
            return new CacheNodeScope.Member(node,row.getProcessId(),row.getEpoch(),row.getLeaseUntil(),state);
        }).toList();
        return new CacheNodeScope(identity.manifestHash(),identity.properties().version(),now,members);
    }
    /** Share-lock identity changes, but allow normal heartbeats throughout the tenant submission transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void validateFrozen(CacheNodeIdentity identity,CacheNodeScope frozen){
        if(frozen==null||!frozen.manifestHash().equals(identity.manifestHash())||frozen.manifestVersion()!=identity.properties().version())throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        // Never create global deployment metadata from a user request. An unregistered cohort stays explicitly UNKNOWN.
        var group=groups.selectOne(new LambdaQueryWrapper<CacheNodeGroupEntity>().eq(CacheNodeGroupEntity::getClusterHash,identity.clusterHash()).last("FOR SHARE"));
        if(group!=null&&(!group.getManifestHash().equals(identity.manifestHash())||group.getManifestVersion().longValue()!=identity.properties().version()))throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        var live=snapshot(identity);
        if(!frozen.members().stream().map(CacheNodeScope.Member::nodeId).toList().equals(identity.properties().expected()))throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        for(int i=0;i<frozen.members().size();i++){
            var before=frozen.members().get(i);var after=live.members().get(i);
            if(!Objects.equals(before.processId(),after.processId())||!Objects.equals(before.epoch(),after.epoch())||after.stateAtCapture().equals("CONFIGURATION_MISMATCH"))
                throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        }
    }
    /** Used only while committing a tenant-scoped confirmation, after applying the cache state outside this transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean holdsLease(CacheNodeIdentity identity,long epoch){
        var actor=ActorContext.require();
        if(actor.kind()!=ActorRef.Kind.SYSTEM_JOB||!actor.subject().equals("cache-node:"+identity.processId()))throw new AccessDeniedException("Node confirmation job required");
        var group=groups.selectOne(new LambdaQueryWrapper<CacheNodeGroupEntity>().eq(CacheNodeGroupEntity::getClusterHash,identity.clusterHash()).last("FOR SHARE"));
        if(group==null||!group.getManifestHash().equals(identity.manifestHash())||group.getManifestVersion().longValue()!=identity.properties().version())return false;
        var row=leases.selectOne(new LambdaQueryWrapper<CacheNodeLeaseEntity>().eq(CacheNodeLeaseEntity::getClusterHash,identity.clusterHash())
                .eq(CacheNodeLeaseEntity::getNodeId,identity.properties().id()).last("FOR SHARE"));
        return row!=null&&row.getProcessId().equals(identity.processId())&&row.getEpoch()==epoch&&row.getManifestHash().equals(identity.manifestHash())&&row.getLeaseUntil().isAfter(groups.now());
    }
}
