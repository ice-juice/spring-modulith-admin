package com.sma.admin.config.internal.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.common.security.TenantWorkQuery;

/** After shared registration (-190) and parameter binding (-180); every registered tenant, including inactive ones. */
@Component
public class DefinedDictionaryBootstrap {
    private final TenantWorkQuery directory;
    private final DefinedDictionaryService dictionaries;
    public DefinedDictionaryBootstrap(TenantWorkQuery directory,DefinedDictionaryService dictionaries){this.directory=directory;this.dictionaries=dictionaries;}
    @EventListener(ApplicationReadyEvent.class) @Order(-175)
    public void migrate() {
        long after=0;
        while(true) {
            var page=directory.page(after,100);if(page.isEmpty())return;
            for(var tenant:page){TenantScope.within(tenant.tenantId(),dictionaries::migrateCurrentTenant);after=tenant.tenantId();}
        }
    }
}
