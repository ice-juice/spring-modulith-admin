package com.sma.admin.common.deployment;

/** Read-only evidence for child tasks owned by an already-admitted request/work or technical coordinator.
 * Implementing Spring components expose one stable, module-owned id; snapshot() must perform no I/O.
 * This is neither admission nor authorization. Library resources and parent work have separate lifetimes.
 */
public interface DeploymentActivity {
    String id();
    Snapshot snapshot();
    record Snapshot(int queued,int running) {
        public Snapshot {
            if(queued<0||running<0)throw new IllegalArgumentException("Negative activity count");
        }
        public boolean quiescent(){return queued==0&&running==0;}
    }
}
