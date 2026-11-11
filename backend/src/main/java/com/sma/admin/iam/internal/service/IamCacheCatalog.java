package com.sma.admin.iam.internal.service;
import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;
@Component
public class IamCacheCatalog implements CacheCatalog {
    @Override public Collection<CacheDescriptor> regions() {
        return List.of(region(IamDisplayCaches.DEPARTMENTS,"organization","department-display",CacheDescriptor.Scope.TENANT_PORTAL,Set.of("save","delete")),
                region(IamDisplayCaches.POSTS,"organization","post-options",CacheDescriptor.Scope.TENANT,Set.of("save","delete")),
                region(IamDisplayCaches.PERMISSIONS,"navigation","menu-metadata",CacheDescriptor.Scope.TENANT_PORTAL,Set.of("save","delete","translation-write","catalog-sync")),
                region(IamDisplayCaches.NAVIGATION,"navigation","navigation-metadata",CacheDescriptor.Scope.TENANT_PORTAL,Set.of("save","delete","translation-write","catalog-sync")),
                region(IamDisplayCaches.USERS,"identity-display","user-display",CacheDescriptor.Scope.TENANT_PORTAL,Set.of("create","update","delete","profile","bootstrap")));
    }
    private CacheDescriptor region(String id,String group,String scenario,CacheDescriptor.Scope scope,Set<String> sources) {
        return new CacheDescriptor(id,"iam","iam.cache."+scenario,group,scenario,CacheDescriptor.Kind.DATA,scope,1,
                CachePolicy.defaults(),Set.of(CacheDescriptor.Operation.CLEAR,CacheDescriptor.Operation.PAUSE,
                        CacheDescriptor.Operation.EDIT_POLICY,CacheDescriptor.Operation.RESET_STATS),sources,"monitor:cache:view");
    }
}
