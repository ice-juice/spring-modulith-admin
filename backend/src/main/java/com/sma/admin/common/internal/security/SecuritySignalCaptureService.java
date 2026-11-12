package com.sma.admin.common.internal.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import com.sma.admin.common.security.*;
import static com.sma.admin.common.security.SecuritySignal.*;

/** Trusted capture only; never looks at request principal/headers/parameters or anonymous default tenant helpers. */
@Service
public class SecuritySignalCaptureService implements SecuritySignalCapture {
    private final RequestSourceScope sources;
    private final Observer observer;
    public SecuritySignalCaptureService(RequestSourceScope sources,com.sma.admin.common.internal.cache.CacheNodeIdentity identity){
        this.sources=sources;
        this.observer=new Observer(identity.properties().cluster(),identity.properties().id(),UUID.fromString(identity.processId()));
    }
    @Override public SecuritySignal capture(Fact fact){
        return new SecuritySignal(UUID.randomUUID(),sources.correlationId().orElseGet(UUID::randomUUID),Instant.now(),
                java.util.Objects.requireNonNull(fact),attribution(),sources.current().orElse(null),new Metadata(observer,sources.http()));
    }
    private Attribution attribution(){
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken)
            return platform(Identity.ANONYMOUS);
        try{
            var actor=ActorContext.require();var entry=TenantEntryScope.optional();
            if(entry.isPresent() && entry.get().tenantId()!=actor.tenantId())return platform(Identity.UNRECOGNIZED);
            if(authentication.getPrincipal() instanceof SmaPrincipal user){
                if(entry.isPresent() && !entry.get().portal().equals(user.portal()))return platform(Identity.UNRECOGNIZED);
                if(user.machine()){
                    if(!user.username().startsWith("app:"))return platform(Identity.UNRECOGNIZED);
                    return new Attribution(Scope.TENANT,Identity.APPLICATION,user.tenantId(),user.username().substring(4),user.portal(),null,null,null);
                }
                return new Attribution(Scope.TENANT,Identity.ADMIN_USER,user.tenantId(),Long.toString(user.userId()),user.portal(),null,null,null);
            }
            if(authentication.getPrincipal() instanceof AppUserPrincipal user)
                return new Attribution(Scope.TENANT,Identity.APP_USER,user.tenantId(),Long.toString(user.subjectId()),null,user.audience(),user.realmId(),user.clientId());
            if(actor.kind()==ActorRef.Kind.SYSTEM_JOB){
                // HTTP code cannot launder attribution through withinJob, even for the same tenant.
                if(sources.current().isPresent() || TenantEntryScope.isHttpRequest() || RequestContextHolder.getRequestAttributes()!=null)
                    return platform(Identity.UNRECOGNIZED);
                // Job work IDs are arbitrary strings and may contain business data; never copy them.
                return new Attribution(Scope.TENANT,Identity.SYSTEM_JOB,actor.tenantId(),null,null,null,null,null);
            }
        }catch(org.springframework.security.access.AccessDeniedException | IllegalArgumentException rejected){
            return platform(Identity.UNRECOGNIZED);
        }
        return platform(Identity.UNRECOGNIZED);
    }
    private Attribution platform(Identity kind){return new Attribution(Scope.PLATFORM,kind,null,null,null,null,null,null);}
}
