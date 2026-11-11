package com.sma.admin.config;

/** Host SPI owned by Config. Tenant supplies deployment authority; a tenant role alone cannot grant it. */
public interface ConfigurationPlatformAccess {
    /** Menu registration only; this does not authorize a platform operation. */
    boolean isPlatformTenant();
    void requireOperator(String permission);
}
