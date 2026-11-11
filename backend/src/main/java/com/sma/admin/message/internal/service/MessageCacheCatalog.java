package com.sma.admin.message.internal.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;

@Component
public class MessageCacheCatalog implements CacheCatalog {
    public Collection<CacheDescriptor> regions(){return List.of(
        protectedRegion("socket-tickets",CacheDescriptor.Scope.TENANT_PORTAL),
        protectedRegion("socket-signals",CacheDescriptor.Scope.TENANT_USER),
        protectedRegion("socket-connections",CacheDescriptor.Scope.NODE));}
    private CacheDescriptor protectedRegion(String name,CacheDescriptor.Scope scope){return new CacheDescriptor("message."+name,"message","message.cache."+name,"security-state",name,CacheDescriptor.Kind.PROTECTED,scope,1,null,Set.of(),Set.of("owning-security-protocol"),"monitor:cache:view");}
}
