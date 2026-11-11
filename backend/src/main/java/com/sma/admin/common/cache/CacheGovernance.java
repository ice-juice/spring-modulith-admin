package com.sma.admin.common.cache;

import java.time.Instant;
import java.util.List;

/** Authorized control of registered DATA regions in the current trusted tenant. Provider switching remains deployment-only. */
public interface CacheGovernance {
    enum Action { SET_POLICY, RESET_POLICY, PAUSE, RESUME, RESET_STATS }
    record Command(Action action,long expectedVersion,CachePolicy policy,String reason) {}
    /** Authoritative desired state; committing it does not claim every node has acknowledged it. */
    record State(String region,long version,long dataRevision,long statsEpoch,boolean paused,
            CachePolicy requestedPolicy,CachePolicy deploymentPolicy,CachePolicy effectivePolicy) {}
    record History(Long id,Instant occurredAt,Long actorId,String portal,Action action,String reason,State before,State after) {}
    State state(String region);
    State change(String region,Command command);
    List<History> history(String region,int limit);
}
