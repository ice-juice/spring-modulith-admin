package com.sma.admin.common.cache;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Tenant/actor-owned governance work. A preview ID is never an authorization credential. */
public interface CacheTasks {
    enum Action { CLEAR, PAUSE, RESUME, RESET_STATS, PREWARM }
    enum State { PREVIEW, QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, CANCELLED, UNKNOWN }
    enum SkipReason { UNKNOWN_REGION, FILTERED_OUT, PROTECTED, ADAPTER_REQUIRED, UNSUPPORTED, FORBIDDEN, DISABLED, PAUSED, UNAVAILABLE }
    record Selection(Set<String> regions,String owner,String group,String scenario) {
        public Selection { regions=regions==null?Set.of():Set.copyOf(regions); }
    }
    record ResourceTarget(String processId,String nodeId,int adapterVersion) {
        public ResourceTarget {
            if(processId==null||!processId.matches("[0-9a-f-]{36}")||nodeId==null||nodeId.isBlank()||nodeId.length()>64||adapterVersion<1)
                throw new IllegalArgumentException("Invalid native resource target");
        }
    }
    record Target(String region,String nameKey,String scope,String managePermission,long controlVersion,String registrationHash,CacheWarmupLoader.Spec warmup,ResourceTarget resource) {
        public Target(String region,String nameKey,String scope,String managePermission,long controlVersion,String registrationHash){this(region,nameKey,scope,managePermission,controlVersion,registrationHash,null,null);}
        public Target(String region,String nameKey,String scope,String managePermission,long controlVersion,String registrationHash,CacheWarmupLoader.Spec warmup){this(region,nameKey,scope,managePermission,controlVersion,registrationHash,warmup,null);}
    }
    record Skipped(String region,SkipReason reason) {}
    /** Only checkpointed successful reads. Neither exact attempts nor retained entries nor all-node warming. */
    record WarmupProgress(long checkpointedReads,String processId,Instant startedAt) {}
    record ResourceProgress(String processId,Instant startedAt,Instant completedAt,Long affectedEntries,Long remainingEntries,Instant routeDeadline) {}
    record Item(String region,String state,Long dataRevision,Long controlVersion,String failureCode,WarmupProgress warmup,ResourceProgress resource) {
        public Item(String region,String state,Long dataRevision,Long controlVersion,String failureCode){this(region,state,dataRevision,controlVersion,failureCode,null,null);}
        public Item(String region,String state,Long dataRevision,Long controlVersion,String failureCode,WarmupProgress warmup){this(region,state,dataRevision,controlVersion,failureCode,warmup,null);}
    }
    record View(String id,Action action,State state,String reason,Instant createdAt,Instant expiresAt,
            boolean cancelRequested,List<Target> targets,List<Skipped> skipped,List<Item> items) {
        public View { targets=List.copyOf(targets);skipped=List.copyOf(skipped);items=List.copyOf(items); }
    }
    View preview(Action action,Selection selection,String reason);
    /** Reusing the original request key returns the same accepted work; never re-executes a failed receipt. */
    View submit(String previewId,String requestKey);
    View get(String id);
    /** Null means legacy/unsupported coverage, never an empty successfully confirmed node set. */
    default CacheNodeScope nodeScope(String id){return null;}
    List<View> recent(int limit);
    View cancel(String id);
}
