package com.sma.admin.common.security;

import java.util.List;

/** Minimal directory for host background scheduling, not an anonymous or tenant-facing HTTP API. */
public interface TenantWorkQuery {
    List<Entry> page(long afterId,int limit);
    record Entry(long tenantId,boolean acceptsBusiness) {}
}
