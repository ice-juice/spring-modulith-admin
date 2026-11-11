package com.sma.admin.config.internal.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.common.security.TenantWorkQuery;

/** Shared definitions exist before tenant binding. No tenant-table access from Config. */
@Component
public class DefinedParameterBootstrap {
    private final TenantWorkQuery directory;
    private final DefinedParameterService layers;
    public DefinedParameterBootstrap(TenantWorkQuery directory,DefinedParameterService layers) { this.directory=directory;this.layers=layers; }
    @EventListener(ApplicationReadyEvent.class) @Order(-180)
    public void migrate() {
        long after=0;
        while(true) {
            var page=directory.page(after,100);if(page.isEmpty())return;
            for(var tenant:page) {
                TenantScope.within(tenant.tenantId(),layers::migrateCurrentTenant);
                after=tenant.tenantId();
            }
        }
    }
}
