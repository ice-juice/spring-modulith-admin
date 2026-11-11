package com.sma.admin.openapi.internal.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;

@Component
public class OpenapiCacheCatalog implements CacheCatalog {
    public Collection<CacheDescriptor> regions(){return List.of(
        protectedRegion("tokens",CacheDescriptor.Scope.TENANT),
        protectedRegion("nonces",CacheDescriptor.Scope.TENANT),
        protectedRegion("login-budgets",CacheDescriptor.Scope.PLATFORM),
        protectedRegion("credential-key",CacheDescriptor.Scope.NODE));}
    private CacheDescriptor protectedRegion(String name,CacheDescriptor.Scope scope){return new CacheDescriptor("openapi."+name,"openapi","openapi.cache."+name,"security-state",name,CacheDescriptor.Kind.PROTECTED,scope,1,null,Set.of(),Set.of("owning-security-protocol"),"monitor:cache:view");}
}
