package com.sma.admin.tenant.internal.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Native durable operation: platform claim, physical migration, then platform activation. */
@Service
public class StorageProvisionService {
    private final StorageProvisionState state;
    private final StorageMigrationService migration;

    public StorageProvisionService(StorageProvisionState state, StorageMigrationService migration) {
        this.state = state;
        this.migration = migration;
    }

    public String provision(long bindingId, long expectedVersion, String operationKey) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Business storage provision cannot join a platform transaction");
        var work = state.begin(bindingId, expectedVersion, operationKey);
        return perform(work);
    }

    public String retryAfterReview(long bindingId, long expectedVersion, String operationKey) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Business storage provision cannot join a platform transaction");
        return perform(state.retryAfterReview(bindingId, expectedVersion, operationKey));
    }

    private String perform(StorageProvisionState.Work work) {
        if (!work.run()) return "SUCCEEDED";
        try {
            state.requireCurrent(work);
            migration.migrate(work.resourceId(), work.revision(),work.storageGroup());
            state.finish(work);
            return "SUCCEEDED";
        } catch (RuntimeException failure) {
            try { state.failed(work); }
            catch (RuntimeException persistence) { failure.addSuppressed(persistence); }
            throw failure;
        }
    }
}
