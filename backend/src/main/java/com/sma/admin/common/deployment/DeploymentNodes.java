package com.sma.admin.common.deployment;

import java.time.Instant;
import java.util.List;

/** Host-owned membership evidence for maintenance operations. Unknown peers fail closed. */
public interface DeploymentNodes {
    record Member(String nodeId, String processId, long epoch, Instant leaseUntil, String state) {}
    record Snapshot(String manifestHash, long manifestVersion, Instant capturedAt, List<Member> members) {
        public Snapshot { members = List.copyOf(members); }
    }
    Snapshot snapshot();
    Member local();
    /** Must run in the caller's platform transaction and serialize membership replacement. */
    void requireCurrent(Snapshot expected);
}
