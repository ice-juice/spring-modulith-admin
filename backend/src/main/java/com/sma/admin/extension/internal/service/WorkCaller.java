package com.sma.admin.extension.internal.service;

import java.util.Set;
import java.util.function.Supplier;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.security.TenantScope;
import com.sma.extension.api.InvocationIdentity;
import com.sma.extension.protocol.Checks;

/** Internal, lexical job scope. Never populated from HTTP headers, bodies or plugin code. */
final class WorkCaller {
    private static final ThreadLocal<Actor> JOB=new ThreadLocal<>();
    private WorkCaller() {}
    record Actor(long tenantId,String subject,String portal,InvocationIdentity.Kind kind,Set<String> permissions) {
        Actor {permissions=Set.copyOf(permissions);}
        InvocationIdentity identity(){return new InvocationIdentity(tenantId,subject,kind);}
    }
    static Actor current() {
        if(JOB.get()!=null)return JOB.get();
        var user=CurrentUser.require();return new Actor(user.tenantId(),Long.toString(user.userId()),user.portal(),InvocationIdentity.Kind.USER,user.permissions());
    }
    static <T>T job(long tenant,String policy,Set<String> permissions,Supplier<T> operation) {
        Checks.require(CurrentUser.optional().isEmpty() && JOB.get()==null,"job-context-must-be-background");
        com.sma.admin.common.deployment.DeploymentEvidence.requireHost();
        var actor=new Actor(tenant,"job:"+policy,"system",InvocationIdentity.Kind.JOB,permissions);actor.identity();
        return TenantScope.within(tenant,() -> {JOB.set(actor);try{return operation.get();}finally{JOB.remove();}});
    }
    static <T>T application(Set<String> permissions,Supplier<T> operation) {
        var principal=CurrentUser.optional().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Application identity required"));
        Checks.require(principal.machine() && principal.username().startsWith("app:") && JOB.get()==null,"application-context-required");
        var actor=new Actor(principal.tenantId(),principal.username(),principal.portal(),InvocationIdentity.Kind.APPLICATION,permissions);
        JOB.set(actor);try{return operation.get();}finally{JOB.remove();}
    }
}
