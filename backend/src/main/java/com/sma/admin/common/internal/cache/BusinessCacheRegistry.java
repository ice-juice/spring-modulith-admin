package com.sma.admin.common.internal.cache;

import java.util.*;
import com.sma.admin.common.cache.*;
import org.springframework.stereotype.Component;

/** Business registrations cannot silently enter platform governance or impersonate another module. */
@Component
public class BusinessCacheRegistry {
    private final Map<String,BusinessCacheCatalog.Region> regions;
    private final CacheRegistry descriptors;
    public BusinessCacheRegistry(List<BusinessCacheCatalog> catalogs,CacheRegistrationOwnership ownership,CacheRegistry platform) {
        var entries=new LinkedHashMap<String,BusinessCacheCatalog.Region>();
        var platformIds=platform.regions().stream().map(CacheDescriptor::id).collect(java.util.stream.Collectors.toSet());
        for(var catalog:catalogs)for(var region:catalog.regions()) {
            ownership.validateBusiness(catalog,region.descriptor());
            if(platformIds.contains(region.descriptor().id())||entries.putIfAbsent(region.descriptor().id(),region)!=null)
                throw new IllegalArgumentException("Duplicate platform/business cache region");
            if(entries.size()>256)throw new IllegalArgumentException("Business cache directory capacity exceeded");
        }
        regions=Map.copyOf(entries);
        descriptors=new CacheRegistry(List.of(()->regions.values().stream().map(BusinessCacheCatalog.Region::descriptor).toList()));
    }
    BusinessCacheCatalog.Region require(String id) {
        var region=regions.get(id);
        if(region==null)throw new IllegalArgumentException("Unregistered business cache region");
        return region;
    }
    CacheRegistry descriptors(){return descriptors;}
    public Collection<BusinessCacheCatalog.Region> regions(){return regions.values();}
}
