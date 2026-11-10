package com.sma.admin.iam.internal.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.iam.internal.persistence.StructureLockMapper;

@Component
public class StructureGuard {
    private final StructureLockMapper mapper;
    public StructureGuard(StructureLockMapper mapper) { this.mapper = mapper; }
    /** Must be called inside the write transaction. InnoDB holds the tenant row lock until commit. */
    public void lock() { mapper.lock(CurrentUser.tenantId()); }
    public void requireFullSubtree(String path, long visibleCount) {
        if (mapper.subtreeCount(path) != visibleCount) throw new AccessDeniedException("Incomplete subtree data access");
    }
}
