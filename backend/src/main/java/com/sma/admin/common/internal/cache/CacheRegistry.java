package com.sma.admin.common.internal.cache;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sma.admin.common.cache.CacheCatalog;
import com.sma.admin.common.cache.CacheDescriptor;
import com.sma.admin.common.cache.CacheRegions;

/** No implicit/dynamically created caches: every use resolves an explicit registration. */
public class CacheRegistry implements CacheRegions {
    private final Map<String, CacheDescriptor> descriptors;

    public CacheRegistry(List<CacheCatalog> catalogs) {
        this(catalogs,null);
    }
    /** Production assembly always supplies ownership; the standalone constructor supports isolated provider contracts. */
    public CacheRegistry(List<CacheCatalog> catalogs,CacheRegistrationOwnership ownership) {
        Map<String, CacheDescriptor> found = new LinkedHashMap<>();
        for (var catalog : catalogs) {
            for (var descriptor : catalog.regions()) {
                if(ownership!=null)ownership.validate(catalog,descriptor);
                if (found.putIfAbsent(descriptor.id(), descriptor) != null) {
                    throw new IllegalArgumentException("Duplicate cache region: " + descriptor.id());
                }
            }
        }
        descriptors = Map.copyOf(found);
    }

    public CacheDescriptor require(String id) {
        var descriptor = descriptors.get(id);
        if (descriptor == null) throw new IllegalArgumentException("Unregistered cache region: " + id);
        return descriptor;
    }

    public Collection<CacheDescriptor> regions() { return descriptors.values(); }
}
