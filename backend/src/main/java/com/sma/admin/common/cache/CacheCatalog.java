package com.sma.admin.common.cache;

import java.util.Collection;

/** Modules contribute immutable registrations. Duplicate region IDs fail startup. */
public interface CacheCatalog {
    Collection<CacheDescriptor> regions();
}
