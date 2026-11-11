package com.sma.admin.common.deployment;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Host-only, transaction-free probes. No filesystem path, business bytes or credentials cross this SPI. */
public interface SharedStorageWitness {
    record Resource(String id,boolean declaredShared) {
        public Resource {if(id==null||!id.matches("[a-z]+\\.[a-z][a-z0-9.-]{0,95}"))throw new IllegalArgumentException("Invalid storage resource identifier");}
    }
    record Scope(String group,String nodeId,UUID processId) {
        public Scope {
            if(group==null||!group.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")||nodeId==null||!nodeId.matches("[a-z][a-z0-9-]{0,47}"))throw new IllegalArgumentException("Invalid storage witness scope");
            Objects.requireNonNull(processId);
        }
    }
    List<Resource> resources();
    /** Repeated identity/challenge verifies the original file; never overwrites an uncertain write. */
    String publish(String resource,Scope scope,byte[] challenge);
    boolean verify(String resource,Scope scope,String expectedDigest);
    /** Remove only this adapter instance's known matching witness; never a peer file or directory tree. */
    void retire(String resource,Scope scope,String expectedDigest);
}
