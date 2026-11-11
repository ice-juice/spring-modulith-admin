package com.sma.admin.common.internal.cache;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.CurrentUser;

@Service
@EnableConfigurationProperties(CacheResourceTaskProperties.class)
public class CacheResourceRegistry implements CacheResources {
    private final CacheRegistry regions;
    private final CacheNodeIdentity identity;
    private final Map<String,CacheResourceAdapter> adapters;
    private final CacheResourceTaskProperties properties;
    public CacheResourceRegistry(CacheRegistry regions,CacheNodeIdentity identity,List<CacheResourceAdapter> candidates,CacheRegistrationOwnership ownership){
        this(regions,identity,candidates,ownership,new CacheResourceTaskProperties(false));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CacheResourceRegistry(CacheRegistry regions,CacheNodeIdentity identity,List<CacheResourceAdapter> candidates,CacheRegistrationOwnership ownership,CacheResourceTaskProperties properties){
        this.regions=regions;this.identity=identity;var registered=new HashMap<String,CacheResourceAdapter>();
        this.properties=properties;
        for(var adapter:candidates){
            var descriptor=regions.require(adapter.region());ownership.validateAdapter(adapter,descriptor);
            if(descriptor.kind()!=CacheDescriptor.Kind.RUNTIME&&descriptor.kind()!=CacheDescriptor.Kind.RESOURCE)throw new IllegalArgumentException("Only runtime/resource regions may supply an adapter");
            if(adapter.operationVersion()<1)throw new IllegalArgumentException("Invalid resource adapter operation version");
            if(registered.putIfAbsent(descriptor.id(),adapter)!=null)throw new IllegalArgumentException("Duplicate resource cache adapter");
        }
        adapters=Map.copyOf(registered);
    }
    @Override public Capability capability(String region,CacheDescriptor.Operation operation){
        if(!CurrentUser.require().permissions().contains("monitor:cache:view"))throw new AccessDeniedException("Cache resource view denied");
        var descriptor=regions.require(region);
        if(descriptor.kind()!=CacheDescriptor.Kind.RUNTIME||!descriptor.operations().contains(operation)||!adapters.containsKey(region)
                ||!Set.of(CacheDescriptor.Operation.CLEAR,CacheDescriptor.Operation.RESET_STATS).contains(operation))return new Capability(false,"UNSUPPORTED");
        if(!properties.enabled())return new Capability(false,"DISABLED");
        try{
            authorize(region,operation);
            if(!inspect(region).state().equals("ACTIVE"))return new Capability(false,"UNAVAILABLE");
            return new Capability(true,"READY");
        }catch(AccessDeniedException denied){return new Capability(false,"FORBIDDEN");}
        catch(RuntimeException unavailable){return new Capability(false,"UNAVAILABLE");}
    }
    /** Permission-only replay/cancellation check is distinct from deployment enablement. */
    public void authorize(String region,CacheDescriptor.Operation operation){
        var descriptor=regions.require(region);var permissions=CurrentUser.require().permissions();
        String permission=switch(operation){case CLEAR->"monitor:cache:clear";case RESET_STATS->"monitor:cache:reset-stats";default->"";};
        if(descriptor.kind()!=CacheDescriptor.Kind.RUNTIME||permission.isEmpty()||!descriptor.operations().contains(operation)
                ||!permissions.contains("monitor:cache:view")||!permissions.contains(permission)||!permissions.contains(descriptor.managePermission())||!adapters.containsKey(region))
            throw new AccessDeniedException("Native resource operation denied");
        adapters.get(region).authorize(operation);
    }
    public CacheTasks.ResourceTarget target(String region){
        var adapter=adapters.get(region);if(adapter==null)throw new IllegalArgumentException("Resource adapter unavailable");
        return new CacheTasks.ResourceTarget(identity.processId(),identity.properties().id(),adapter.operationVersion());
    }
    public int operationVersion(String region){var adapter=adapters.get(region);return adapter==null?0:adapter.operationVersion();}
    public boolean enabled(){return properties.enabled();}
    public CacheResourceAdapter.Outcome execute(String region,CacheDescriptor.Operation operation,CacheTasks.ResourceTarget binding){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Native resource effects cannot hold a task transaction");
        if(!binding.processId().equals(identity.processId())||binding.adapterVersion()!=operationVersion(region)||!properties.enabled())throw new IllegalStateException("Native resource target changed");
        authorize(region,operation);return Objects.requireNonNull(adapters.get(region).execute(operation));
    }
    @Override public Observation inspect(String region){
        if(!CurrentUser.require().permissions().contains("monitor:cache:view"))throw new AccessDeniedException("Cache resource view denied");
        var descriptor=regions.require(region);
        if(descriptor.kind()==CacheDescriptor.Kind.DATA)throw new IllegalArgumentException("Use the data cache inspection contract");
        if(descriptor.kind()==CacheDescriptor.Kind.PROTECTED)return unavailable(region,"protected","PROTECTED","common.cache.lifecycle.protected");
        String provider=descriptor.kind()==CacheDescriptor.Kind.RUNTIME?"node-runtime":"owner-resource";
        var adapter=adapters.get(region);if(adapter==null)return unavailable(region,provider,"ADAPTER_REQUIRED","common.cache.lifecycle.adapter-required");
        try{
            var sample=adapter.inspect();
            if(!sample.lifecycleKey().startsWith(descriptor.owner()+"."))throw new IllegalArgumentException("Resource lifecycle key must belong to its module");
            return new Observation(region,provider,identity.processId(),Instant.now(),descriptor.kind()==CacheDescriptor.Kind.RUNTIME?"ACTIVE":"READ_ONLY",
                    sample.entries(),sample.entryLimit(),sample.bytes(),sample.protectedEntries(),sample.lifecycleKey());
        }catch(AccessDeniedException denied){return unavailable(region,provider,"RESTRICTED","common.cache.lifecycle.restricted");}
        catch(RuntimeException unavailable){return unavailable(region,provider,"UNAVAILABLE","common.cache.lifecycle.unavailable");}
    }
    private Observation unavailable(String region,String provider,String state,String message){return new Observation(region,provider,identity.processId(),Instant.now(),state,-1,-1,-1,-1,message);}
}
