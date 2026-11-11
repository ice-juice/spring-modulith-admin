package com.sma.admin.common.internal.cache;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import com.sma.admin.common.cache.*;

@Service
public class CacheInspectionService implements CacheInspection {
    private final CacheRegistry registry;
    private final CacheProperties properties;
    private final ManagedCache cache;
    // Identifies one process lifetime; it is not a cluster lease or a distributed-ID worker assignment.
    private final String processId;
    public CacheInspectionService(CacheRegistry registry, CacheProperties properties, ManagedCache cache) {
        this(registry,properties,cache,new CacheNodeIdentity(new CacheNodeProperties(null,null,null,null)));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CacheInspectionService(CacheRegistry registry, CacheProperties properties, ManagedCache cache,CacheNodeIdentity identity) {
        this.registry=registry;this.properties=properties;this.cache=cache;
        this.processId=identity.processId();
    }
    @Override public Observation inspect(String region) {
        var descriptor=registry.require(region);
        if(descriptor.kind()!=CacheDescriptor.Kind.DATA)throw new IllegalArgumentException("An owning adapter is required");
        Map<String,String> sources=new LinkedHashMap<>();
        for(var field:List.of("ttl","negativeTtl","maxEntries","maxBytes","maxEntryBytes"))sources.put(field,"DECLARED");
        source(sources,properties.defaults(),"DEPLOYMENT_DEFAULT");
        source(sources,properties.regions().get(region),"DEPLOYMENT_REGION");
        var effective=properties.policy(region,descriptor.policy());
        if(effective.maxBytes()>properties.maxBytes())sources.put("maxBytes","DEPLOYMENT_PROVIDER_LIMIT");
        if(effective.maxEntryBytes()>properties.maxBytes())sources.put("maxEntryBytes","DEPLOYMENT_PROVIDER_LIMIT");
        effective=CacheRuntimePolicy.deployment(effective,properties.maxBytes());
        Control control=null;
        if(cache instanceof DurableManagedCache durable){
            try {
                var state=durable.controlState(region);
                var runtime=state.override();
                if(runtime!=null){
                    runtimeSource(sources,"ttl",runtime.ttl().compareTo(effective.ttl())<=0);
                    runtimeSource(sources,"negativeTtl",runtime.negativeTtl().compareTo(effective.negativeTtl())<=0);
                    runtimeSource(sources,"maxEntries",runtime.maxEntries()<=effective.maxEntries());
                    runtimeSource(sources,"maxBytes",runtime.maxBytes()<=effective.maxBytes());
                    runtimeSource(sources,"maxEntryBytes",runtime.maxEntryBytes()<=effective.maxEntryBytes());
                    effective=CacheRuntimePolicy.effective(effective,runtime);
                }
                control=new Control(state.controlVersion(),state.revision(),state.statsEpoch(),state.paused(),runtime,true);
            }catch(CacheUnavailableException unavailable){
                control=new Control(-1,-1,-1,null,null,false);effective=null;sources.replaceAll((field,source)->"UNAVAILABLE");
            }
        }
        String provider=properties.provider().name().toLowerCase(Locale.ROOT);
        var snapshot=cache.snapshot(region);
        return new Observation(provider,processId,Instant.now(),provider.equals("redis")?"SHARED_TENANT_REGION":"PROCESS_TENANT_REGION",
                "PROCESS_OBSERVATION_LIFETIME",provider.equals("redis")?"ENCODED_FIELDS_ESTIMATE":"SERIALIZED_PAYLOAD",
                effective==null?-1:provider.equals("redis")?Math.min(effective.maxBytes(),properties.maxBytes()):properties.maxBytes(),
                provider.equals("redis")?"SHARED_TENANT_REGION":"PROCESS_ALL_REGIONS",
                new Policy(descriptor.policy(),effective,sources),snapshot,control);
    }
    private void runtimeSource(Map<String,String> sources,String field,boolean withinDeployment){sources.put(field,withinDeployment?"TENANT_RUNTIME":"DEPLOYMENT_CAP");}
    private void source(Map<String,String> target, CacheProperties.RegionOverride override,String source) {
        if(override==null)return;
        if(override.ttl()!=null)target.put("ttl",source);
        if(override.negativeTtl()!=null)target.put("negativeTtl",source);
        if(override.maxEntries()!=null)target.put("maxEntries",source);
        if(override.maxBytes()!=null)target.put("maxBytes",source);
        if(override.maxEntryBytes()!=null)target.put("maxEntryBytes",source);
    }
}
