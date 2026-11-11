package com.sma.admin.common.cache;

import java.time.Instant;
import java.util.Map;

/** Read-only deployment policy and bounded statistics. Never exposes values, keys or connection credentials. */
public interface CacheInspection {
    record Policy(CachePolicy declared, CachePolicy effective, Map<String,String> sources) {
        public Policy { sources=Map.copyOf(sources); }
    }
    /** Confirmed only for this process. Unknown control state is not an unpaused state. */
    record Control(long version,long dataRevision,long statsEpoch,Boolean paused,CachePolicy requestedPolicy,boolean confirmed) {}
    record Observation(String provider, String processId, Instant sampledAt, String entryScope,
            String counterScope, String byteMeasurement, long providerByteLimit, String providerByteLimitScope,
            Policy policy, CacheSnapshot snapshot,Control control) {
        public Observation(String provider,String processId,Instant sampledAt,String entryScope,String counterScope,String byteMeasurement,
                long providerByteLimit,String providerByteLimitScope,Policy policy,CacheSnapshot snapshot){
            this(provider,processId,sampledAt,entryScope,counterScope,byteMeasurement,providerByteLimit,providerByteLimitScope,policy,snapshot,null);
        }
    }
    Observation inspect(String region);
}
