package com.sma.admin.common.cache;

import java.time.Instant;

/** Host-stamped, current-process observations. Protected state exposes no mutation through this interface. */
public interface CacheResources {
    record Observation(String region,String provider,String processId,Instant sampledAt,String state,
            long entries,long entryLimit,long bytes,long protectedEntries,String lifecycleKey) {}
    Observation inspect(String region);
    record Capability(boolean available,String reason) {}
    default Capability capability(String region,CacheDescriptor.Operation operation){return new Capability(false,"UNSUPPORTED");}
}
