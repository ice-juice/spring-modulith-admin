package com.sma.admin.tenant.internal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import com.sma.admin.common.deployment.DeploymentEvidence;
import org.springframework.stereotype.Component;

/** Each configured process proves only its own drain; failed proof remains pending. */
@Component
public class StorageDrainWorker {
    private static final Logger log = LoggerFactory.getLogger(StorageDrainWorker.class);
    private final StorageSwitchState state;
    private long afterId;
    public StorageDrainWorker(StorageSwitchState state) { this.state = state; }

    @Scheduled(fixedDelay = 1000L)
    public synchronized void tick() {
        // Technical proof for already-draining bindings must remain available during deployment drain.
        DeploymentEvidence.requireHost();
        try {
            var tasks = state.draining(afterId);
            if (tasks.isEmpty()) afterId = 0;
            for (long id : tasks) {
                afterId = id;
                try { state.acknowledgeLocal(id); }
                catch (RuntimeException unavailable) { log.warn("Business storage drain proof deferred for task {}", id); }
            }
        } catch (RuntimeException unavailable) {
            log.warn("Business storage drain scan deferred");
        }
    }
}
