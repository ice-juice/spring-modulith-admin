package com.sma.admin.tenant.internal.service;

import com.sma.admin.common.request.BusinessLedger;
import com.sma.admin.common.request.BusinessLedgerProvider;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.deployment.DeploymentNodes;
import com.sma.admin.tenant.internal.persistence.TenantRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Resolve the trusted tenant's active binding before any business transaction starts. */
@Service
public class StorageLedgerProvider implements BusinessLedgerProvider {
    private final StorageDirectory directory;
    private final StoragePoolRegistry pools;
    private final TenantRecordMapper tenants;
    private final StorageSessionTracker sessions;
    private final StorageSessionLeaseStore leases;
    private final DeploymentNodes nodes;

    public StorageLedgerProvider(StorageDirectory directory, StoragePoolRegistry pools, TenantRecordMapper tenants) {
        this(directory,pools,tenants,new StorageSessionTracker(),null,null);
    }
    public StorageLedgerProvider(StorageDirectory directory, StoragePoolRegistry pools,
                                 TenantRecordMapper tenants, StorageSessionTracker sessions) {
        this(directory,pools,tenants,sessions,null,null);
    }
    @Autowired
    public StorageLedgerProvider(StorageDirectory directory, StoragePoolRegistry pools,
                                 TenantRecordMapper tenants, StorageSessionTracker sessions,
                                 StorageSessionLeaseStore leases, DeploymentNodes nodes) {
        this.directory=directory; this.pools=pools; this.tenants=tenants; this.sessions=sessions;
        this.leases=leases; this.nodes=nodes;
    }
    @Override public BusinessLedger.Session open(long tenantId, String storageGroup) {
        ActorContext.requireTenant(tenantId);
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Business storage resource must be selected before a transaction");
        var tenant=tenants.selectById(tenantId);
        if (tenant==null || !"ACTIVE".equals(tenant.getStatus()))
            throw new IllegalStateException("Business storage tenant is unavailable");
        var binding=directory.bindingForCurrentTenant(storageGroup);
        if (binding==null) return null;
        if (!"ACTIVE".equals(binding.getState()) || !"v1".equals(binding.getSchemaCompatibility()))
            throw new IllegalStateException("Business storage binding is not provisioned");
        // Durable admission below is authoritative; a settled switch may leave this process locally blocked.
        sessions.unblock(tenantId, storageGroup);
        var resource=directory.resource(binding.getResourceId(), binding.getResourceRevision());
        if (resource==null || !"REGISTERED".equals(resource.getState()))
            throw new IllegalStateException("Business storage resource is unavailable");
        if (leases == null || nodes == null) return sessions.track(tenantId, storageGroup, pools.open(resource));
        var lease = leases.register(binding, nodes.local());
        BusinessLedger.Session physical;
        try { physical = pools.open(resource); }
        catch (RuntimeException failure) {
            try { leases.release(lease); }
            catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
            throw failure;
        }
        return sessions.track(tenantId, storageGroup, physical, () -> leases.release(lease));
    }
}
