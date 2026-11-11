package com.sma.admin.config.internal.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;

@Component
public class ConfigCacheCatalog implements CacheCatalog {
    public static final String PARAMETERS = "config.parameters";
    public static final String DICTIONARIES = "config.dictionary-options";
    public static final String PREFERENCES = "config.preferences";
    @Override public Collection<CacheDescriptor> regions() {
        return List.of(new CacheDescriptor(PARAMETERS, "config", "config.cache.parameters", "basic-config", "parameter-read",
                CacheDescriptor.Kind.DATA, CacheDescriptor.Scope.TENANT, 1, CachePolicy.defaults(),
                operations(), Set.of("save", "delete", "reset-builtins", "initialize", "legacy-brand-archive",
                    "definition-override", "definition-inherit", "definition-migration", "shared-publication-revision"), "monitor:cache:view"),
            new CacheDescriptor(DICTIONARIES, "config", "config.cache.dictionaries", "basic-config", "dictionary-options",
                CacheDescriptor.Kind.DATA, CacheDescriptor.Scope.TENANT, 1, CachePolicy.defaults(), dictionaryOperations(),
                Set.of("type-write", "item-write", "translation-write", "initialize", "definition-override", "definition-private-item",
                    "definition-enabled", "definition-migration", "shared-publication-revision"), "monitor:cache:view"),
            new CacheDescriptor(PREFERENCES, "config", "config.cache.preferences", "personal-settings", "raw-preference-read",
                CacheDescriptor.Kind.DATA, CacheDescriptor.Scope.TENANT_USER, 1, CachePolicy.defaults(), operations(),
                Set.of("save", "reset"), "monitor:cache:view"));
    }
    private Set<CacheDescriptor.Operation> operations(){return Set.of(CacheDescriptor.Operation.CLEAR,CacheDescriptor.Operation.PAUSE,CacheDescriptor.Operation.EDIT_POLICY,CacheDescriptor.Operation.RESET_STATS);}
    private Set<CacheDescriptor.Operation> dictionaryOperations(){var result=new java.util.HashSet<>(operations());result.add(CacheDescriptor.Operation.PREWARM);return Set.copyOf(result);}
}
