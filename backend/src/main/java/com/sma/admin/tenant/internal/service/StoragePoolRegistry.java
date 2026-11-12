package com.sma.admin.tenant.internal.service;

import com.sma.admin.common.request.BusinessLedger;
import com.sma.admin.common.request.BusinessLedgerResourceFactory;
import com.sma.admin.common.request.BusinessOutboxConsumer;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.tenant.internal.persistence.StorageResourceEntity;
import com.sma.admin.tenant.storage.StorageCredentialResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** One physical pool per resource revision, shared by bound tenants with bounded leases. */
@Component
@EnableConfigurationProperties(StoragePoolProperties.class)
public class StoragePoolRegistry implements AutoCloseable {
    private record Key(String id, long revision) {}
    private record Descriptor(String host, int port, String databaseName, String tlsMode,
                              String secretRef, int maxConnections, int connectTimeoutMillis) {
        static Descriptor of(StorageResourceEntity row) {
            return new Descriptor(row.getDbHost(), row.getDbPort(), row.getDatabaseName(), row.getTlsMode(),
                    row.getSecretRef(), row.getPoolMax(), row.getConnectTimeoutMillis());
        }
    }
    private static final class Slot {
        final Descriptor descriptor;
        final int maxConnections;
        final HikariDataSource pool;
        final BusinessLedgerResourceFactory.Resource ledger;
        int leases;
        Instant idleSince;
        Slot(Descriptor descriptor, int maxConnections, HikariDataSource pool,
             BusinessLedgerResourceFactory.Resource ledger) {
            this.descriptor=descriptor; this.maxConnections=maxConnections; this.pool=pool; this.ledger=ledger;
        }
    }

    private final BusinessLedgerResourceFactory ledgers;
    private final ObjectProvider<StorageCredentialResolver> credentials;
    private final StoragePoolProperties budget;
    private final Clock clock;
    private final DeploymentWork admission;
    private final Map<Key, Slot> pools = new HashMap<>();
    private boolean closed;

    @Autowired
    public StoragePoolRegistry(BusinessLedgerResourceFactory ledgers,
            ObjectProvider<StorageCredentialResolver> credentials, StoragePoolProperties budget, DeploymentWork admission) {
        this(ledgers, credentials, budget, Clock.systemUTC(), admission);
    }
    StoragePoolRegistry(BusinessLedgerResourceFactory ledgers,
            ObjectProvider<StorageCredentialResolver> credentials, StoragePoolProperties budget, Clock clock, DeploymentWork admission) {
        this.ledgers=ledgers; this.credentials=credentials; this.budget=budget; this.clock=clock; this.admission=admission;
    }

    public synchronized BusinessLedger.Session open(StorageResourceEntity resource) {
        if (closed) throw new IllegalStateException("Business storage pools are closing");
        if (resource == null || !"REGISTERED".equals(resource.getState()))
            throw new IllegalStateException("Business storage resource is unavailable");
        var key = new Key(resource.getResourceId(), resource.getRevision());
        var slot = pools.get(key);
        if (slot == null) {
            if (!hasCapacity(resource)) evictIdle(true);
            requireCapacity(resource);
            slot = create(resource);
            pools.put(key, slot);
        } else if (!slot.descriptor.equals(Descriptor.of(resource))) {
            throw new IllegalStateException("Business storage resource revision changed in place");
        }
        if (slot.leases >= slot.maxConnections * 2)
            throw new IllegalStateException("Business storage resource lease budget is exhausted");
        slot.leases++;
        slot.idleSince=null;
        var selected=slot;
        try { return slot.ledger.open(() -> release(selected)); }
        catch (RuntimeException failure) { release(selected); throw failure; }
    }

    private Slot create(StorageResourceEntity resource) {
        var resolver=credentials.getIfAvailable();
        if (resolver == null) throw new IllegalStateException("Business storage credential resolver is not configured");
        var secret=resolver.resolve(resource.getSecretRef());
        if (secret == null) throw new IllegalStateException("Business storage secret is unavailable");
        var configuration = new HikariConfig();
        configuration.setJdbcUrl(StorageJdbcUrl.of(resource));
        configuration.setUsername(secret.username()); configuration.setPassword(secret.password());
        configuration.setMaximumPoolSize(resource.getPoolMax()); configuration.setMinimumIdle(0);
        configuration.setConnectionTimeout(Math.max(250, resource.getConnectTimeoutMillis()));
        configuration.setInitializationFailTimeout(-1);
        configuration.setPoolName("business-"+resource.getResourceId()+"-"+resource.getRevision());
        var pool = new HikariDataSource(configuration);
        try {
            return new Slot(Descriptor.of(resource), resource.getPoolMax(), pool,
                    ledgers.preparePhysical(resource.getResourceId(), resource.getRevision(), pool));
        } catch (RuntimeException failure) {
            pool.close(); throw failure;
        }
    }

    private boolean hasCapacity(StorageResourceEntity next) {
        int total=0, host=0;
        for (var slot : pools.values()) {
            total+=slot.maxConnections;
            if (slot.descriptor.host().equals(next.getDbHost())) host+=slot.maxConnections;
        }
        return pools.size() < budget.maxPools() && total+next.getPoolMax()<=budget.maxConnections()
                && host+next.getPoolMax()<=budget.maxConnectionsPerHost();
    }
    private void requireCapacity(StorageResourceEntity next) {
        if (!hasCapacity(next)) throw new IllegalStateException("Business storage node pool budget is exhausted");
    }

    private synchronized void release(Slot slot) {
        if (--slot.leases < 0) throw new IllegalStateException("Business storage lease underflow");
        if (slot.leases == 0) {
            slot.idleSince=clock.instant();
            if (closed) remove(slot);
        }
    }

    @Scheduled(fixedDelay = 60_000L)
    public synchronized void evictIdle() {
        DeploymentEvidence.requireHost();
        if (closed) return;
        var accepted=admission.tryStart();if(accepted.isEmpty())return;
        try(var permit=accepted.orElseThrow()){evictIdle(false);}
    }

    private void evictIdle(boolean forCapacity) {
        var now=clock.instant();
        var iterator=pools.values().iterator();
        while (iterator.hasNext()) {
            var slot=iterator.next();
            if (slot.leases==0 && slot.idleSince!=null
                    && (forCapacity || !now.isBefore(slot.idleSince.plus(budget.idleTtl())))) {
                iterator.remove(); slot.pool.close();
            }
        }
    }
    private void remove(Slot slot) {
        pools.values().removeIf(existing -> existing == slot);
        slot.pool.close();
    }
    public synchronized int activePools() { return pools.size(); }
    public synchronized List<String> activeResourceKeys() {
        return pools.keySet().stream().map(key -> key.id() + ":" + key.revision()).sorted().toList();
    }
    /** Uses an existing physical pool only; polling never creates a pool for an idle tenant. */
    public boolean deliverOne(String resourceId, long revision, List<BusinessOutboxConsumer> consumers) {
        return deliverNext(resourceId,revision,consumers).orElse(false);
    }
    public Long authoritativeRevision(String resourceId, long revision, long tenantId, String region) {
        Slot slot;
        synchronized (this) {
            slot = pools.get(new Key(resourceId, revision));
            if (closed || slot == null || slot.leases >= slot.maxConnections * 2) return null;
            slot.leases++;
        }
        try { return slot.ledger.authoritativeRevision(tenantId, region); }
        finally { release(slot); }
    }
    /** Empty means admission refused: a scheduler must preserve its current resource cursor. */
    java.util.Optional<Boolean> deliverNext(String resourceId, long revision, List<BusinessOutboxConsumer> consumers) {
        DeploymentEvidence.requireHost();
        var accepted=admission.tryStart();if(accepted.isEmpty())return java.util.Optional.empty();
        try(var permit=accepted.orElseThrow()){
            return java.util.Optional.of(deliverAdmitted(resourceId,revision,consumers));
        }
    }
    private boolean deliverAdmitted(String resourceId, long revision, List<BusinessOutboxConsumer> consumers) {
        Slot slot;
        synchronized (this) {
            slot = pools.get(new Key(resourceId, revision));
            if (closed || slot == null || slot.leases >= slot.maxConnections * 2) return false;
            slot.leases++;
        }
        try { return slot.ledger.deliverOne(consumers); }
        finally { release(slot); }
    }
    @Override public synchronized void close() {
        closed=true;
        evictIdle(true);
    }
}
