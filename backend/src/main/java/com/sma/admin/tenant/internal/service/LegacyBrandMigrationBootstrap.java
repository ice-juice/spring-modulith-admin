package com.sma.admin.tenant.internal.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.common.security.TenantWorkQuery;

/** Readiness is not published until registered tenants have completed the upgrade. Failures are never ignored. */
@Component
public class LegacyBrandMigrationBootstrap {
    private final TenantWorkQuery directory;
    private final LegacyBrandMigrationService migration;
    public LegacyBrandMigrationBootstrap(TenantWorkQuery directory,LegacyBrandMigrationService migration) { this.directory=directory;this.migration=migration; }
    @EventListener(ApplicationReadyEvent.class) @Order(-100)
    public void migrate() {
        long after=0;
        while(true) {
            var page=directory.page(after,100);if(page.isEmpty()) return;
            for(var tenant:page) {
                try { TenantScope.within(tenant.tenantId(),migration::migrateCurrentTenant); }
                catch(RuntimeException failure) { throw new IllegalStateException("Legacy brand migration failed for tenant "+tenant.tenantId()+"; resolve source data/conflicts before rollout",failure); }
                after=tenant.tenantId();
            }
        }
    }
}
