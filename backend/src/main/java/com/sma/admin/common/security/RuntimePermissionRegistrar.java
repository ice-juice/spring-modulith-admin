package com.sma.admin.common.security;

import java.util.Set;

/** Registers validated extension declarations for the current tenant/portal; never grants them to users or roles. */
public interface RuntimePermissionRegistrar {
    void extensionEntries(Set<String> keys);
}
