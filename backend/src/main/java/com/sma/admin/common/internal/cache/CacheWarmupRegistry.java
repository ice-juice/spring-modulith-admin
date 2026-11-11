package com.sma.admin.common.internal.cache;

import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.CurrentUser;

@Component
@EnableConfigurationProperties(CacheWarmupProperties.class)
public class CacheWarmupRegistry implements CachePrewarming {
    private record Registered(CacheWarmupLoader.Spec spec,CacheWarmupLoader loader) {}
    private final Map<String,Registered> loaders;
    private final CacheRegistry regions;
    private final CacheProperties cache;
    private final CacheWarmupProperties properties;
    private final CacheRevisionStore revisions;
    public CacheWarmupRegistry(List<CacheWarmupLoader> candidates,CacheRegistry regions,CacheProperties cache,CacheWarmupProperties properties,CacheRevisionStore revisions){
        this(candidates,regions,cache,properties,revisions,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CacheWarmupRegistry(List<CacheWarmupLoader> candidates,CacheRegistry regions,CacheProperties cache,CacheWarmupProperties properties,CacheRevisionStore revisions,CacheRegistrationOwnership ownership){
        this.regions=regions;this.cache=cache;this.properties=properties;this.revisions=revisions;
        var found=new HashMap<String,Registered>();
        for(var loader:candidates){
            var spec=Objects.requireNonNull(loader.specification());var descriptor=regions.require(spec.region());
            if(ownership!=null)ownership.validateLoader(loader,descriptor);
            if(descriptor.kind()!=CacheDescriptor.Kind.DATA||!Set.of(CacheDescriptor.Scope.TENANT,CacheDescriptor.Scope.TENANT_PORTAL).contains(descriptor.scope()))
                throw new IllegalArgumentException("Warmup requires non-private tenant data");
            if(found.putIfAbsent(spec.region(),new Registered(spec,loader))!=null)throw new IllegalArgumentException("Duplicate cache warmup loader");
        }
        loaders=Map.copyOf(found);
    }
    public CacheWarmupLoader.Spec specification(String region){var entry=loaders.get(region);return entry==null?null:entry.spec();}
    public CacheWarmupLoader require(String region){var entry=loaders.get(region);if(entry==null)throw new IllegalArgumentException("No registered warmup loader");return entry.loader();}
    @Override public Eligibility inspect(String region){
        var permissions=CurrentUser.require().permissions();
        if(!permissions.contains("monitor:cache:view"))throw new AccessDeniedException("Cache warmup view denied");
        var descriptor=regions.require(region);var spec=specification(region);String reason="READY";
        if(spec==null||!descriptor.operations().contains(CacheDescriptor.Operation.PREWARM))reason="UNSUPPORTED";
        else if(!properties.enabled()||cache.provider()==CacheProperties.Provider.NONE)reason="DISABLED";
        else if(!permissions.contains("monitor:cache:prewarm")||!permissions.contains(descriptor.managePermission())||!permissions.contains(spec.readPermission()))reason="FORBIDDEN";
        else if(revisions.state(region).paused())reason="PAUSED";
        return new Eligibility(reason.equals("READY"),reason,spec);
    }
}
