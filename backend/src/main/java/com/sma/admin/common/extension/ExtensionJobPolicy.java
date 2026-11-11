package com.sma.admin.common.extension;

import java.util.Set;

/** Registered by a stable business module. Resolve trusted event/task ids under the supplied tenant scope. */
public interface ExtensionJobPolicy {
    String id();
    Set<String> permissions();
    Job resolve(String objectId);
    record Job(String deploymentId,String pluginId,String entryId,String workKey,byte[] input) {
        public Job {input=input.clone();}
        @Override public byte[] input(){return input.clone();}
    }
}
