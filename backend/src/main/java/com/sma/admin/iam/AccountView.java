package com.sma.admin.iam;
import java.time.Instant;
import java.util.Set;

public record AccountView(Long id,long tenantId,String username,String name,String portal,Long departmentId,
        boolean superAdmin,boolean enabled,Set<Long> roleIds,Set<Long> postIds,String locale,String timeZone,
        Instant passwordChangedAt) {
    public AccountView { roleIds=Set.copyOf(roleIds); postIds=Set.copyOf(postIds); }
}
