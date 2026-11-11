package com.sma.admin.common.internal.cache;

import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;

@Component
public class CommonCacheCatalog implements CacheCatalog {
    public Collection<CacheDescriptor> regions(){return List.of(
        new CacheDescriptor("common.sql-statistics","common","common.cache.sql-statistics","runtime-statistics","jdbc-statistics",CacheDescriptor.Kind.RUNTIME,CacheDescriptor.Scope.TENANT,1,null,Set.of(CacheDescriptor.Operation.RESET_STATS),Set.of("statistics-reset","process-stop"),"monitor:sql:reset"),
        protectedRegion("request-ledger",CacheDescriptor.Scope.TENANT_PORTAL_USER),protectedRegion("request-keyring",CacheDescriptor.Scope.NODE),
        protectedRegion("id-allocation",CacheDescriptor.Scope.PLATFORM));}
    private CacheDescriptor protectedRegion(String name,CacheDescriptor.Scope scope){return new CacheDescriptor("common."+name,"common","common.cache."+name,"security-state",name,CacheDescriptor.Kind.PROTECTED,scope,1,null,Set.of(),Set.of(name.equals("id-allocation")?"owning-id-protocol":"owning-request-protocol"),"monitor:cache:view");}
}
