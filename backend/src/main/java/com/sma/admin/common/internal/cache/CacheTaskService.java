package com.sma.admin.common.internal.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import com.sma.admin.common.*;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheTaskStore;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;

@Service
@EnableConfigurationProperties(CacheTaskProperties.class)
public class CacheTaskService implements CacheTasks {
    private final CacheTaskPlanner planner;
    private final CacheTaskStore store;
    private final RequestExecution requests;
    private final TenantEntryQuery tenants;
    public CacheTaskService(CacheTaskPlanner planner,CacheTaskStore store,RequestExecution requests,TenantEntryQuery tenants){
        this.planner=planner;this.store=store;this.requests=requests;this.tenants=tenants;
    }
    @Override public View preview(Action action,Selection selection,String reason){
        requireView();
        if(reason==null||reason.isBlank()||reason.length()>256||reason.chars().anyMatch(c->c<32||c==127))throw new BizException(CommonErrorCode.BAD_REQUEST);
        if(!CurrentUser.require().permissions().contains(CacheTaskPlanner.permission(action)))throw new AccessDeniedException("Cache task action denied");
        var generation=generation();
        requests.claimBudget("cache.preview",30,Duration.ofMinutes(1));
        requests.claimTenantBudget("cache.preview",120,Duration.ofMinutes(1));
        var plan=planner.preview(action,selection);
        return store.create(action,reason.trim(),new CacheTaskStore.Frozen(plan.targets(),plan.skipped()),generation);
    }
    @Override public View submit(String previewId,String requestKey){
        requireView();var initial=store.get(previewId);
        if(requestKey==null||!requestKey.matches("[A-Za-z0-9_.:-]{16,128}"))throw new RequestRejected(RequestRejected.Reason.KEY_REQUIRED,0);
        var command=new RequestCommand("cache.submit","cache.task",previewId,requestKey,previewId.getBytes(StandardCharsets.US_ASCII),CacheTaskPlanner.permission(initial.action()));
        var outcome=requests.executeLocal(command,new RequestExecution.Work<View>(){
            @Override public void authorize(){
                requireView();generation();var current=store.get(previewId);planner.authorize(current.action(),current.targets());
            }
            @Override public View execute(){
                return store.enqueue(previewId,generation(),()->planner.validateFrozen(initial.targets()));
            }
        });
        if(outcome.receipt().state()!=RequestReceipt.State.SUCCEEDED)throw new RequestStateException(outcome.receipt());
        return store.get(previewId);
    }
    @Override public View get(String id){requireView();return store.get(id);}
    @Override public CacheNodeScope nodeScope(String id){requireView();return store.nodeScope(id);}
    @Override public List<View> recent(int limit){requireView();return store.recent(limit);}
    @Override public View cancel(String id){
        requireView();var current=store.get(id);planner.authorizeCancellation(current.action(),current.targets());return store.cancel(id);
    }
    private long generation(){
        var state=tenants.securityState(CurrentUser.tenantId()).orElseThrow(()->new AccessDeniedException("Tenant no longer available"));
        if(!state.acceptsBusiness())throw new AccessDeniedException("Tenant does not accept work");return state.generation();
    }
    private void requireView(){if(!CurrentUser.require().permissions().contains("monitor:cache:view"))throw new AccessDeniedException("Cache task view denied");}
}
