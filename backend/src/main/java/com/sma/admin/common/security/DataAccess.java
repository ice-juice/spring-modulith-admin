package com.sma.admin.common.security;

import java.util.Set;

/** Union of data grants from the user's active roles. Tenant isolation is always applied separately. */
public record DataAccess(boolean all, Set<Long> departmentIds, boolean self) {
    public DataAccess {
        departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
    }
    public static DataAccess unrestricted() { return new DataAccess(true, Set.of(), false); }
    public static DataAccess none() { return new DataAccess(false, Set.of(), false); }
    public static DataAccess own() { return new DataAccess(false, Set.of(), true); }
}
