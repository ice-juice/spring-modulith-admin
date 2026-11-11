package com.sma.admin.common.internal.cache;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class CacheTransactions {
    private CacheTransactions() {}
    static boolean bypass() { return TransactionSynchronizationManager.isActualTransactionActive(); }
    /** Caller captures trusted scope before registering. Durable invalidation is added in S2c. */
    static void afterCommit(Runnable action) {
        if (bypass()) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("Cache invalidation requires transaction synchronization");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else action.run();
    }
}
