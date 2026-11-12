package com.sma.admin.common.cache;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** Static business-owned regions. Separate from the platform CacheCatalog and its control-plane revisions. */
public interface BusinessCacheCatalog {
    Collection<Region> regions();
    record Region(String storageGroup,CacheDescriptor descriptor) {
        public Region {
            Objects.requireNonNull(descriptor,"descriptor");
            if(storageGroup==null || !storageGroup.matches("[a-z][a-z0-9-]{1,63}")
                    || !(storageGroup.equals(descriptor.owner())||storageGroup.startsWith(descriptor.owner()+"-")))
                throw new IllegalArgumentException("Business storage group must belong to its cache owner");
            if(descriptor.kind()!=CacheDescriptor.Kind.DATA
                    || !descriptor.operations().equals(Set.of(CacheDescriptor.Operation.CLEAR)))
                throw new IllegalArgumentException("Business cache supports rebuildable DATA with transactional invalidation only");
        }
    }
}
