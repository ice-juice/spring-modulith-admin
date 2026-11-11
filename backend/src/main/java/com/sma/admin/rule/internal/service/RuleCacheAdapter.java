package com.sma.admin.rule.internal.service;

import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.CacheResourceAdapter;
import com.sma.admin.common.cache.CacheDescriptor;
import com.sma.admin.common.security.CurrentUser;

@Component
public class RuleCacheAdapter implements CacheResourceAdapter {
    private final RuleOperationsService operations;
    public RuleCacheAdapter(RuleOperationsService operations){this.operations=operations;}
    public String region(){return "rule.runtime";}
    public Snapshot inspect(){
        if(!CurrentUser.require().permissions().contains("rule:maintenance:view"))throw new AccessDeniedException("Rule resource view denied");
        var sample=operations.resources(); // Preserves the owner's ALL data scope and tenant checks.
        return new Snapshot(sample.get("expressions")+sample.get("plans"),sample.get("expressionTenantLimit")+sample.get("planTenantLimit"),-1,-1,"rule.cache.lifecycle.runtime");
    }
    public void authorize(CacheDescriptor.Operation operation){
        var permissions=CurrentUser.require().permissions();
        if(operation!=CacheDescriptor.Operation.CLEAR||!permissions.contains("rule:maintenance:clean")||!permissions.contains("rule:maintenance:view"))throw new AccessDeniedException("Rule runtime cleanup denied");
        operations.resources(); // Reuses the owner's ALL-scope guard.
    }
    public Outcome execute(CacheDescriptor.Operation operation){
        authorize(operation);var result=operations.clearResources();var retained=operations.resources();
        return new Outcome(result.get("expressionsCleared")+result.get("idlePlansCleared"),retained.get("expressions")+retained.get("plans"));
    }
}
