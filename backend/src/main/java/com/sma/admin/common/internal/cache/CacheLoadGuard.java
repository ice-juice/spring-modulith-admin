package com.sma.admin.common.internal.cache;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import com.sma.admin.common.cache.CacheDescriptor;
import com.sma.admin.common.cache.CacheUnavailableException;

/** No unbounded queue on a failed cache backend; only active source work consumes permits. */
final class CacheLoadGuard {
    private final Semaphore total;
    private final Map<String, Semaphore> regions;
    private final LongAdder rejected = new LongAdder();

    CacheLoadGuard(CacheRegistry registry, CacheResilienceProperties properties) {
        total = new Semaphore(properties.maxConcurrentLoads());
        regions = registry.regions().stream().filter(region -> region.kind() == CacheDescriptor.Kind.DATA)
                .collect(Collectors.toUnmodifiableMap(CacheDescriptor::id, region -> new Semaphore(properties.maxConcurrentPerRegion())));
    }

    <T> Supplier<T> protect(String region, Supplier<T> loader) {
        var permits = regions.get(region);
        if (permits == null || loader == null) throw new IllegalArgumentException("Unregistered cache source");
        return () -> {
            if (Thread.currentThread().isInterrupted() || !total.tryAcquire()) { rejected.increment(); throw new CacheUnavailableException(); }
            try {
                if (!permits.tryAcquire()) { rejected.increment(); throw new CacheUnavailableException(); }
                try { return loader.get(); }
                finally { permits.release(); }
            } finally { total.release(); }
        };
    }
    long rejected() { return rejected.sum(); }
}
