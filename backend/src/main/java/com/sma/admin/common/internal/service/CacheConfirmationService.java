package com.sma.admin.common.internal.service;

import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.security.CurrentUser;

@Service
public class CacheConfirmationService implements CacheConfirmations {
    private final CacheTaskStore tasks;
    private final CacheTaskMapper taskRows;
    private final CacheConfirmationMapper confirmations;
    private final CacheNodeLeaseStore leases;
    private final CacheNodeIdentity identity;
    private final CacheNodeRuntime runtime;
    public CacheConfirmationService(CacheTaskStore tasks,CacheTaskMapper taskRows,CacheConfirmationMapper confirmations,CacheNodeLeaseStore leases,CacheNodeIdentity identity,CacheNodeRuntime runtime){
        this.tasks=tasks;this.taskRows=taskRows;this.confirmations=confirmations;this.leases=leases;this.identity=identity;this.runtime=runtime;
    }
    @Override @Transactional(readOnly=true)
    public Report task(String id){
        requireView();var task=tasks.get(id);var scope=tasks.nodeScope(id);var now=confirmations.now();
        if(task.action()==CacheTasks.Action.PREWARM)return new Report(id,task.state().name(),"NOT_APPLICABLE_PREWARM",now,false,false,"NOT_MEASURED",List.of());
        boolean nativeTargets=task.targets().stream().anyMatch(target->target.resource()!=null);
        if(!task.targets().isEmpty()&&task.targets().stream().allMatch(target->target.resource()!=null))return new Report(id,task.state().name(),"NOT_APPLICABLE_RESOURCE",now,false,false,"NOT_MEASURED",List.of());
        if(scope==null)return new Report(id,task.state().name(),"LEGACY_UNKNOWN",now,false,false,"NOT_MEASURED",List.of());
        var row=taskRows.selectOne(new LambdaQueryWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,id)
                .eq(CacheTaskEntity::getCreatedBy,CurrentUser.userId()).eq(CacheTaskEntity::getPortal,CurrentUser.portal()));
        var stored=confirmations.selectList(new LambdaQueryWrapper<CacheConfirmationEntity>().eq(CacheConfirmationEntity::getTaskId,row.getId()));
        var indexed=new HashMap<String,CacheConfirmationEntity>();stored.forEach(entry->indexed.put(entry.getRegion()+"/"+entry.getNodeId(),entry));
        var current=leases.snapshot(identity);var members=new HashMap<String,CacheNodeScope.Member>();current.members().forEach(member->members.put(member.nodeId(),member));
        var items=new HashMap<String,CacheTasks.Item>();task.items().forEach(item->items.put(item.region(),item));
        var result=new ArrayList<Node>();
        for(var target:task.targets().stream().filter(target->target.resource()==null).toList())for(var member:scope.members()){
            var item=items.get(target.region());var evidence=indexed.get(target.region()+"/"+member.nodeId());
            boolean matches=evidence==null||(Objects.equals(evidence.getExpectedProcess(),member.processId())&&Objects.equals(evidence.getExpectedEpoch(),member.epoch())
                    &&evidence.getManifestHash().equals(scope.manifestHash())&&item!=null&&Objects.equals(evidence.getRequiredRevision(),item.dataRevision())
                    &&Objects.equals(evidence.getRequiredControl(),item.controlVersion()));
            String confirmation=item==null||!item.state().equals("APPLIED")?"NOT_APPLIED":evidence==null?"UNRECORDED":evidence.getState();
            if(!matches){confirmation="EVIDENCE_MISMATCH";evidence=null;}
            if(confirmation.equals("CONFIRMED")&&(evidence.getObservedRevision()>evidence.getRequiredRevision()||evidence.getObservedControl()>evidence.getRequiredControl()))confirmation="CONFIRMED_NEWER";
            result.add(new Node(target.region(),member.nodeId(),member.processId(),member.epoch(),availability(scope,current,member,members.get(member.nodeId())),
                    confirmation,item==null?null:item.dataRevision(),item==null?null:item.controlVersion(),evidence==null?null:evidence.getObservedRevision(),
                    evidence==null?null:evidence.getObservedControl(),evidence==null?null:evidence.getCreatedAt(),evidence==null?null:evidence.getConfirmedAt(),evidence==null?"":evidence.getLastError(),evidence==null?null:evidence.getLastAttemptAt()));
        }
        boolean confirmed=!result.isEmpty()&&result.stream().allMatch(node->Set.of("CONFIRMED","CONFIRMED_NEWER").contains(node.confirmation()));
        boolean online=!result.isEmpty()&&result.stream().allMatch(node->node.availability().equals("ONLINE"));
        return new Report(id,task.state().name(),nativeTargets?"DATA_TARGETS_ONLY":"DECLARED",now,confirmed&&!nativeTargets,online,"NOT_MEASURED",result);
    }
    @Override @Transactional(readOnly=true)
    public Runtime nodes(){requireView();var status=runtime.status();return new Runtime(leases.snapshot(identity),identity.processId(),status.state(),status.lastHeartbeatAt());}
    private String availability(CacheNodeScope expected,CacheNodeScope current,CacheNodeScope.Member before,CacheNodeScope.Member live){
        if(!expected.manifestHash().equals(current.manifestHash())||expected.manifestVersion()!=current.manifestVersion())return "CONFIGURATION_CHANGED";
        if(live!=null&&live.stateAtCapture().equals("CONFIGURATION_MISMATCH"))return "CONFIGURATION_MISMATCH";
        if(before.processId()==null)return "UNKNOWN_AT_PREVIEW";
        if(live==null||live.processId()==null)return "UNKNOWN";
        if(!before.processId().equals(live.processId())||!Objects.equals(before.epoch(),live.epoch()))return "REPLACED";
        return live.stateAtCapture();
    }
    private void requireView(){if(!CurrentUser.require().permissions().contains("monitor:cache:view"))throw new AccessDeniedException("Cache confirmation view denied");}
}
