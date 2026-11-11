package com.sma.admin.common.internal.cache;

import java.time.Duration;
import com.sma.admin.common.cache.CachePolicy;

/** Deployment bounds remain authoritative even after an operator tightens deployment configuration. */
final class CacheRuntimePolicy {
    private CacheRuntimePolicy() {}
    static CachePolicy deployment(CachePolicy policy,long providerByteLimit){
        long bytes=Math.min(policy.maxBytes(),providerByteLimit);
        return new CachePolicy(policy.ttl(),policy.negativeTtl(),policy.maxEntries(),bytes,(int)Math.min(policy.maxEntryBytes(),bytes));
    }
    static CachePolicy effective(CachePolicy deployment,CachePolicy runtime) {
        if(runtime==null)return deployment;
        Duration ttl=min(deployment.ttl(),runtime.ttl());
        long bytes=Math.min(deployment.maxBytes(),runtime.maxBytes());
        return new CachePolicy(ttl,min(ttl,min(deployment.negativeTtl(),runtime.negativeTtl())),
                Math.min(deployment.maxEntries(),runtime.maxEntries()),bytes,
                (int)Math.min(bytes,Math.min(deployment.maxEntryBytes(),runtime.maxEntryBytes())));
    }
    private static Duration min(Duration a,Duration b){return a.compareTo(b)<=0?a:b;}
}
