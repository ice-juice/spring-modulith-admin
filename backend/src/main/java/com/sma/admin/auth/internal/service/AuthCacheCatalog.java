package com.sma.admin.auth.internal.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;

@Component
public class AuthCacheCatalog implements CacheCatalog {
    public Collection<CacheDescriptor> regions(){return List.of(
        protectedRegion("sessions",CacheDescriptor.Scope.TENANT_PORTAL_USER),
        protectedRegion("captchas",CacheDescriptor.Scope.TENANT),
        protectedRegion("mfa-challenges",CacheDescriptor.Scope.TENANT),
        protectedRegion("security-state",CacheDescriptor.Scope.TENANT),
        protectedRegion("login-budgets",CacheDescriptor.Scope.TENANT));}
    private CacheDescriptor protectedRegion(String name,CacheDescriptor.Scope scope){return new CacheDescriptor("auth."+name,"auth","auth.cache."+name,"security-state",name,CacheDescriptor.Kind.PROTECTED,scope,1,null,Set.of(),Set.of("owning-security-protocol"),"monitor:cache:view");}
}
