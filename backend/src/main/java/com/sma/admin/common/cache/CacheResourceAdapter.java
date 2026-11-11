package com.sma.admin.common.cache;

/** Owning-module inspection of non-serializable runtime objects/resources. No cache values, keys, paths or credentials. */
public interface CacheResourceAdapter {
    record Snapshot(long entries,long entryLimit,long bytes,long protectedEntries,String lifecycleKey) {
        public Snapshot {
            if(entries < -1||entryLimit < -1||bytes < -1||protectedEntries < -1||lifecycleKey==null||!lifecycleKey.matches("[a-z]+\\.[a-zA-Z0-9.-]{1,150}"))
                throw new IllegalArgumentException("Invalid resource cache observation");
        }
    }
    String region();
    /** Increment for incompatible operation semantics. Old implementations remain inspection-only. */
    default int operationVersion(){return 1;}
    /** Must check the original business read permission and data scope; no side effects or unbounded filesystem scans. */
    Snapshot inspect();
    /** Side-effect-free validation of the original business permission and scope. */
    default void authorize(CacheDescriptor.Operation operation){throw new org.springframework.security.access.AccessDeniedException("Resource operation unsupported");}
    record Outcome(long affectedEntries,long remainingEntries) {
        public Outcome {if(affectedEntries < -1||remainingEntries < -1)throw new IllegalArgumentException("Invalid resource operation result");}
    }
    /** Native local effect: finite execution, no business writes/network side effects; a thrown exception means outcome unknown. */
    default Outcome execute(CacheDescriptor.Operation operation){throw new UnsupportedOperationException("Resource operation unsupported");}
}
