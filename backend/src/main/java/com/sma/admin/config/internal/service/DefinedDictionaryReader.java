package com.sma.admin.config.internal.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.config.DictionarySnapshot;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;

/** Cache orchestration outside short source transactions; keys carry definition, publication, tenant revision and language. */
@Service
public class DefinedDictionaryReader {
    private final ConfigurationCatalogRegistry catalog;
    private final DefinedDictionaryService dictionaries;
    private final ManagedCache cache;
    public DefinedDictionaryReader(ConfigurationCatalogRegistry catalog,DefinedDictionaryService dictionaries,ManagedCache cache){
        this.catalog=catalog;this.dictionaries=dictionaries;this.cache=cache;
    }
    public boolean registered(String key){return key!=null&&catalog.find(Kind.DICTIONARY,key).isPresent();}
    public Optional<DictionarySnapshot> read(String key) {
        if(!registered(key))return Optional.empty();
        var frame=dictionaries.capture(key);String language=DefinedDictionaryService.language();
        return Optional.of(cache.get(ConfigCacheCatalog.DICTIONARIES,frame.cacheKey(language),DictionarySnapshot.class,()->dictionaries.resolve(frame,language),
                value->value!=null&&value.key().equals(frame.definition().key())&&value.revision().equals(frame.revision())));
    }
}
