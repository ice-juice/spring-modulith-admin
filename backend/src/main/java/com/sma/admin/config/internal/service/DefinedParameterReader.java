package com.sma.admin.config.internal.service;

import java.util.Optional;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.config.ParameterSnapshot;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;

/** Cache orchestration stays outside its short source transactions. An existing caller transaction still bypasses ManagedCache. */
@Service
public class DefinedParameterReader {
    private final ConfigurationCatalogRegistry catalog;
    private final DefinedParameterService layers;
    private final ManagedCache cache;
    public DefinedParameterReader(ConfigurationCatalogRegistry catalog,DefinedParameterService layers,ManagedCache cache) {
        this.catalog=catalog;this.layers=layers;this.cache=cache;
    }
    public Optional<ParameterSnapshot> read(String key) {
        var entry=catalog.find(Kind.PARAMETER,key);if(entry.isEmpty()) return Optional.empty();
        if(entry.get().parameter().securitySensitive()) return Optional.of(layers.get(entry.get().key()).snapshot());
        var frame=layers.capture(entry.get().key());
        var result=cache.get(ConfigCacheCatalog.PARAMETERS,frame.cacheKey(LocaleContextHolder.getLocale().toLanguageTag()),ParameterSnapshot.class,
                ()->layers.resolve(frame),value->value!=null && value.key().equals(entry.get().key())
                    && value.valueType()==entry.get().parameter().valueType() && value.revision().equals(frame.revision()));
        return Optional.of(result);
    }
}
