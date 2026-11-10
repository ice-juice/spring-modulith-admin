package com.sma.admin.iam;

import java.util.Set;
import com.sma.admin.common.security.DataAccess;

/** Read-only role grant expansion, shared with authentication. */
public interface RoleAccessQuery {
    DataAccess resolve(Set<Long> roleIds, Long departmentId);
}
