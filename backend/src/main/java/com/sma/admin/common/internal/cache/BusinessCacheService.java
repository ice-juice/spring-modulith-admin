package com.sma.admin.common.internal.cache;

import java.time.Clock;
import java.util.*;
import java.util.function.*;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.persistence.DataSourceIdentity;
import com.sma.admin.common.request.BusinessLedger;
import com.sma.admin.common.security.ActorContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Shared backend, source-local authority. No background polling, new Redis clients or platform revision reads. */
@Service
public class BusinessCacheService implements BusinessCache {
    static final int MAX_SOURCES=8;
    private record Source(String group,DataSourceIdentity identity) {}
    private static final class Slot {
        final ManagedCache cache;final CacheInvalidationTarget invalidation;int active;
        Slot(ManagedCache cache,CacheInvalidationTarget invalidation){this.cache=cache;this.invalidation=invalidation;}
    }
    private final BusinessCacheRegistry directory;
    private final BusinessLedger ledger;
    private final CacheProperties settings;
    private final CacheResilienceProperties limits;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final int maximumSources;
    private final LinkedHashMap<Source,Slot> slots=new LinkedHashMap<>(16,.75f,true);
    private final Map<Long,Integer> tenants=new HashMap<>();
    private int active;
    public BusinessCacheService(BusinessCacheRegistry directory,BusinessLedger ledger,CacheProperties settings,
            CacheResilienceProperties limits,ObjectProvider<StringRedisTemplate> redis) {
        this.directory=directory;this.ledger=ledger;this.settings=settings;this.limits=limits;this.redis=redis;
        maximumSources=(int)Math.min(MAX_SOURCES,Math.min(settings.maxBytes()/1024,settings.maxScopes()));
    }
    @Override public <T>T get(String region,String key,Class<T> type,Supplier<T> loader,Predicate<T> cacheable) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Business cache reads must start before the resource transaction");
        long tenant=ActorContext.tenantId();
        var registration=directory.require(region);
        CacheLookup.resolve(directory.descriptors(),region,key,type);
        Objects.requireNonNull(loader);Objects.requireNonNull(cacheable);
        enter(tenant);
        try(var session=ledger.open(registration.storageGroup())) {
            var identity=Objects.requireNonNull(session.resourceIdentity());
            if(identity.equals(DataSourceIdentity.UNKNOWN))throw new IllegalStateException("Business cache requires a known physical source");
            var source=new Source(registration.storageGroup(),identity);
            var slot=borrow(source);
            try {
                Supplier<T> read=()->session.query(loader);
                if(settings.provider()==CacheProperties.Provider.NONE)return read.get();
                // The authority read is outside the loader transaction and uses the same pinned resource.
                long revision=session.authoritativeRevision(region);
                try {slot.invalidation.applyRevision(region,tenant,revision);}
                catch(CacheUnavailableException|IllegalStateException unavailable){return read.get();}
                return slot.cache.get(region,key,type,read,cacheable);
            } finally {release(slot);}
        } finally {leave(tenant);}
    }
    private synchronized void enter(long tenant) {
        int count=tenants.getOrDefault(tenant,0);
        int perTenant=Math.min(limits.maxConcurrentPerRegion(),Math.max(1,limits.maxConcurrentLoads()/2));
        if(Thread.currentThread().isInterrupted()||active>=limits.maxConcurrentLoads()||count>=perTenant)
            throw new CacheUnavailableException();
        tenants.put(tenant,count+1);active++;
    }
    private synchronized void leave(long tenant) {
        int count=tenants.get(tenant)-1;if(count==0)tenants.remove(tenant);else tenants.put(tenant,count);active--;
    }
    private synchronized Slot borrow(Source source) {
        var slot=slots.get(source);
        if(slot==null) {
            if(slots.size()>=maximumSources) {
                var iterator=slots.entrySet().iterator();boolean removed=false;
                while(iterator.hasNext())if(iterator.next().getValue().active==0){iterator.remove();removed=true;break;}
                if(!removed)throw new CacheUnavailableException();
            }
            slot=create(source);slots.put(source,slot);
        }
        slot.active++;return slot;
    }
    private synchronized void release(Slot slot){slot.active--;}
    private Slot create(Source source) {
        String namespace=namespace(settings.namespace(),source.group(),source.identity());
        // Local byte/scope ceilings are divided across the bounded physical source slots.
        var properties=new CacheProperties(settings.provider(),namespace,settings.deploymentMode(),
                settings.maxBytes()/maximumSources,Math.max(1,settings.maxScopes()/maximumSources),settings.ttlJitter(),Map.of(),settings.defaults());
        if(settings.provider()==CacheProperties.Provider.REDIS) {
            var cache=new RedisManagedCache(directory.descriptors(),properties,limits,new RedisCacheStore(redis.getObject(),namespace));
            return new Slot(cache,cache);
        }
        var cache=new LocalManagedCache(directory.descriptors(),properties,Clock.systemUTC(),limits);
        return new Slot(cache,cache);
    }
    static String namespace(String deployment,String group,DataSourceIdentity source) {
        return "business1-"+CacheLookup.hash(deployment+"|"+group+"|"+source.resourceId()+"|"+source.revision()).substring(0,48);
    }
}
