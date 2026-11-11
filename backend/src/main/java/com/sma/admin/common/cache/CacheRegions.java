package com.sma.admin.common.cache;

import java.util.Collection;

/** Read-only registered metadata; governance still authorizes every operation separately. */
public interface CacheRegions {
    Collection<CacheDescriptor> regions();
}
