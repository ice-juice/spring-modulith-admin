package com.sma.admin.common.cache;

import java.time.Instant;
import java.util.List;

/** Historical application evidence and current node availability are different facts. */
public interface CacheConfirmations {
    record Node(String region,String nodeId,String expectedProcessId,Long expectedEpoch,String availability,
            String confirmation,Long requiredRevision,Long requiredControlVersion,Long observedRevision,Long observedControlVersion,
            Instant requestedAt,Instant confirmedAt,String lastError,Instant lastAttemptAt) {}
    record Report(String taskId,String logicalState,String coverage,Instant checkedAt,boolean allTargetsConfirmed,
            boolean allExpectedProcessesOnline,String reclamation,List<Node> nodes) {
        public Report {nodes=List.copyOf(nodes);}
    }
    record Runtime(CacheNodeScope scope,String localProcessId,String observerState,Instant lastHeartbeatAt) {}
    Report task(String id);
    Runtime nodes();
}
