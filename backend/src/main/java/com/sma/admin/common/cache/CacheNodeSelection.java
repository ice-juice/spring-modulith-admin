package com.sma.admin.common.cache;

/** Exact receiving-process binding, not node discovery, authorization or an arbitrary forwarding API. */
public interface CacheNodeSelection {
    String NODE_HEADER="X-SMA-Cache-Node";
    String PROCESS_HEADER="X-SMA-Cache-Process";
    record Selected(String nodeId,String processId) {}
    /** Both absent selects the receiving process. Otherwise both must match its current identity. */
    Selected select(String nodeId,String processId);
}
