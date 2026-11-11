package com.sma.admin.config.internal.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Each definition is one short, repeatable host transaction; any incompatible contract stops readiness. */
@Component
public class SharedConfigurationBootstrap {
    private final ConfigurationCatalogRegistry catalog;
    private final SharedConfigurationService shared;
    public SharedConfigurationBootstrap(ConfigurationCatalogRegistry catalog, SharedConfigurationService shared) { this.catalog = catalog; this.shared = shared; }
    @EventListener(ApplicationReadyEvent.class) @Order(-190)
    public void initialize() {
        for (var entry : catalog.entries()) if (entry.shared()) shared.initialize(entry.kind(), entry.key());
    }
}
