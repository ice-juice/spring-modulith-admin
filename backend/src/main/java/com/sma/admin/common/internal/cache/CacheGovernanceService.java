package com.sma.admin.common.internal.cache;

import java.util.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.*;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.internal.service.CacheControlAuditStore;
import com.sma.admin.common.internal.service.CacheRevisionStore;

@Service
public class CacheGovernanceService implements CacheGovernance {
    private final CacheRegistry registry;
    private final CacheProperties properties;
    private final CacheRevisionStore revisions;
    private final CacheControlAuditStore audits;
    private final ManagedCache cache;
    public CacheGovernanceService(CacheRegistry registry,CacheProperties properties,CacheRevisionStore revisions,
            CacheControlAuditStore audits,ManagedCache cache){
        this.registry=registry;this.properties=properties;this.revisions=revisions;this.audits=audits;this.cache=cache;
    }
    @Override public State state(String region){
        require("monitor:cache:view");var descriptor=descriptor(region);return view(descriptor,revisions.state(region));
    }
    @Override @Transactional public State change(String region,Command command){
        CurrentUser.require();
        if(command==null||command.action()==null||command.expectedVersion()<0||command.reason()==null||command.reason().isBlank()
                ||command.reason().length()>256||command.reason().chars().anyMatch(c->c<32||c==127))throw invalid();
        var descriptor=descriptor(region);var operation=operation(command.action());
        require(permission(command.action()));require(descriptor.managePermission());
        if(descriptor.kind()!=CacheDescriptor.Kind.DATA||!descriptor.operations().contains(operation))throw new AccessDeniedException("Cache capability denied");
        if(command.action()==Action.SET_POLICY){
            if(command.policy()==null)throw invalid();
            var ceiling=deployment(descriptor);var proposed=command.policy();
            if(!CacheRuntimePolicy.effective(ceiling,proposed).equals(proposed)
                    ||proposed.ttl().getNano()%1_000_000!=0||proposed.negativeTtl().getNano()%1_000_000!=0)throw invalid();
        }else if(command.policy()!=null)throw invalid();
        var before=revisions.lockedState(region);
        if(before.controlVersion()!=command.expectedVersion())throw new CacheControlConflictException();
        final CacheRevisionStore.State after;
        try {after=revisions.change(region,command.expectedVersion(),switch(command.action()){
            case SET_POLICY,RESET_POLICY -> CacheRevisionStore.Change.POLICY;
            case PAUSE -> CacheRevisionStore.Change.PAUSE;
            case RESUME -> CacheRevisionStore.Change.RESUME;
            case RESET_STATS -> CacheRevisionStore.Change.RESET_STATS;
        },command.policy());}catch(OptimisticLockingFailureException stale){throw new CacheControlConflictException();}
        var result=view(descriptor,after);
        audits.append(region,command.action(),command.reason().trim(),CurrentUser.portal(),view(descriptor,before),result);
        long tenant=CurrentUser.tenantId();
        CacheTransactions.afterCommit(()->CacheExecutionScope.within(tenant,()->{
            if(cache instanceof DurableManagedCache durable)durable.controlCommitted(region,after);
            return null;
        }));
        return result;
    }
    @Override @Transactional(readOnly=true) public List<History> history(String region,int limit){
        require("monitor:cache:view");var descriptor=descriptor(region);require(descriptor.managePermission());
        if(limit<1||limit>100)throw invalid();
        return audits.history(region,limit);
    }
    private State view(CacheDescriptor descriptor,CacheRevisionStore.State state){
        var deployment=deployment(descriptor);
        return new State(descriptor.id(),state.controlVersion(),state.revision(),state.statsEpoch(),state.paused(),state.override(),deployment,
                CacheRuntimePolicy.effective(deployment,state.override()));
    }
    private CacheDescriptor descriptor(String region){
        var descriptor=registry.regions().stream().filter(row->row.id().equals(region)).findFirst().orElseThrow(()->new BizException(CommonErrorCode.DATA_NOT_FOUND));
        if(descriptor.kind()!=CacheDescriptor.Kind.DATA)throw new AccessDeniedException("Cache data governance required");
        return descriptor;
    }
    private void require(String permission){if(!CurrentUser.require().permissions().contains(permission))throw new AccessDeniedException("Cache governance denied");}
    private CachePolicy deployment(CacheDescriptor descriptor){return CacheRuntimePolicy.deployment(properties.policy(descriptor.id(),descriptor.policy()),properties.maxBytes());}
    private BizException invalid(){return new BizException(CommonErrorCode.BAD_REQUEST);}
    private CacheDescriptor.Operation operation(Action action){return switch(action){case SET_POLICY,RESET_POLICY->CacheDescriptor.Operation.EDIT_POLICY;case PAUSE,RESUME->CacheDescriptor.Operation.PAUSE;case RESET_STATS->CacheDescriptor.Operation.RESET_STATS;};}
    private String permission(Action action){return switch(action){case SET_POLICY,RESET_POLICY->"monitor:cache:policy";case PAUSE,RESUME->"monitor:cache:pause";case RESET_STATS->"monitor:cache:reset-stats";};}
}
