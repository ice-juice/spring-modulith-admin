package com.sma.admin.common.security;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;

/** Immutable identity resolved by authentication. Never construct from request headers or body fields. */
public record SmaPrincipal(long userId, String username, long tenantId, String portal, Long departmentId,
        boolean superAdmin, DataAccess dataAccess, Set<String> permissions, String timeZone,boolean machine) implements Principal {
    public SmaPrincipal(long userId,String username,long tenantId,String portal,Long departmentId,boolean superAdmin,DataAccess dataAccess,Set<String> permissions,String timeZone){
        this(userId,username,tenantId,portal,departmentId,superAdmin,dataAccess,permissions,timeZone,false);
    }
    public static SmaPrincipal forApplication(long tenantId,String portal,String appId){return new SmaPrincipal(0,"app:"+appId,tenantId,portal,null,false,DataAccess.none(),Set.of(),"UTC",true);}
    public SmaPrincipal {
        if ((!machine && userId <= 0) || tenantId <= 0) { throw new IllegalArgumentException("Identity ids must be positive"); }
        Objects.requireNonNull(username);
        Objects.requireNonNull(portal);
        Objects.requireNonNull(dataAccess);
        permissions = Set.copyOf(permissions);
        if(machine&&(userId!=0||superAdmin||dataAccess.all()||dataAccess.self()||!dataAccess.departmentIds().isEmpty()||!permissions.isEmpty()))throw new IllegalArgumentException("Application identity cannot receive human grants");
    }
    @Override public String getName() { return username; }
}
