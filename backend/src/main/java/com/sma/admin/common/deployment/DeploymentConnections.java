package com.sma.admin.common.deployment;

/** Read-only local connection lifecycle evidence; snapshot() must not perform I/O.
 * Pending upgrades and real I/O remain counted until actual handoff/completion, never a timeout.
 * Zero counts do not prove HTTP, background work or other adapters have drained.
 */
public interface DeploymentConnections {
    String id();
    Snapshot snapshot();
    record Snapshot(int handshakes,int connections,int operations) {
        public Snapshot {
            if(handshakes<0||connections<0||operations<0)throw new IllegalArgumentException("Negative connection lifecycle count");
        }
        public boolean drained(){return handshakes==0&&connections==0&&operations==0;}
    }
}
