package com.sma.admin.iam.internal.service;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.cache.CacheTaskActorResolver;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.*;

@Service
public class CacheTaskActorService implements CacheTaskActorResolver {
    private final AccountQuery accounts;
    private final PermissionQuery permissions;
    private final RoleAccessQuery scopes;
    public CacheTaskActorService(AccountQuery accounts,PermissionQuery permissions,RoleAccessQuery scopes){
        this.accounts=accounts;this.permissions=permissions;this.scopes=scopes;
    }
    @Override @Transactional(readOnly=true)
    public Optional<SmaPrincipal> resolve(long userId,String portal){
        var job=ActorContext.require();
        if(job.kind()!=ActorRef.Kind.SYSTEM_JOB||!job.subject().matches("cache-task:[0-9a-f-]{36}"))throw new AccessDeniedException("Persisted cache work required");
        var account=accounts.findById(userId).filter(row->row.tenantId()==job.tenantId()&&row.enabled()&&row.portal().equals(portal));
        if(account.isEmpty())return Optional.empty();
        var user=account.get();var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();
        // Like login's identity-resolution scope: only resolve authoritative IAM grants here, never execute work with this temporary identity.
        var identity=new SmaPrincipal(user.id(),user.username(),user.tenantId(),user.portal(),user.departmentId(),user.superAdmin(),DataAccess.unrestricted(),Set.of(),user.timeZone());
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of()));SecurityContextHolder.setContext(context);
        try{return Optional.of(new SmaPrincipal(user.id(),user.username(),user.tenantId(),user.portal(),user.departmentId(),user.superAdmin(),
                user.superAdmin()?DataAccess.unrestricted():scopes.resolve(user.roleIds(),user.departmentId()),permissions.keys(user.roleIds(),user.superAdmin()),user.timeZone()));}
        finally{SecurityContextHolder.setContext(previous);}
    }
}
