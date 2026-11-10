package com.sma.admin.iam.internal.service;

/** Module-internal reference guards are added alongside user/role relationships. */
public interface OrganizationReference {
    default boolean departmentUsed(long id) { return false; }
    default boolean postUsed(long id) { return false; }
    default boolean roleUsed(long id) { return false; }
    default boolean permissionUsed(long id) { return false; }
}
