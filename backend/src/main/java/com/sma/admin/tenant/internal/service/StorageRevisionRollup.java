package com.sma.admin.tenant.internal.service;

import com.sma.admin.common.request.BusinessRevisionRollup;
import com.sma.admin.common.security.ActorContext;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Bounded, resource-specific observation; a failed poll remains explicitly stale. */
@Component
public class StorageRevisionRollup {
    private final StoragePoolRegistry pools;
    private final BusinessRevisionRollup observed = new BusinessRevisionRollup(Clock.systemUTC(), Duration.ofSeconds(10), 4096);

    public StorageRevisionRollup(StoragePoolRegistry pools) { this.pools = pools; }

    public BusinessRevisionRollup.State sample(String resourceId, long revision, String region) {
        long tenant = ActorContext.tenantId();
        return observed.sample(resourceId, revision, region, () -> {
            var value = pools.authoritativeRevision(resourceId, revision, tenant, region);
            if (value == null) throw new IllegalStateException("Business resource is not active on this node");
            return value;
        });
    }

    public BusinessRevisionRollup.State observe(String resourceId, long revision, String region) {
        return observed.observe(resourceId, revision, region);
    }

}
