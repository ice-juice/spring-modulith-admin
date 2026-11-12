package com.sma.admin.tenant.internal.service;

import com.sma.admin.tenant.storage.StorageMigrationEvidence;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Physical verifier runs outside the platform task transaction. Missing verifier fails closed. */
@Service
public class StorageSwitchCoordinator {
    private final StorageSwitchState state;
    private final ObjectProvider<StorageMigrationEvidence> verifier;

    public StorageSwitchCoordinator(StorageSwitchState state, ObjectProvider<StorageMigrationEvidence> verifier) {
        this.state = state; this.verifier = verifier;
    }

    public StorageSwitchState.Task begin(long bindingId, long expectedVersion, String targetId,
                                         long targetRevision, String operationKey) {
        requireOutsideTransaction();
        return state.begin(bindingId, expectedVersion, targetId, targetRevision, operationKey);
    }

    public StorageSwitchState.Task verifyCopy(long id) {
        requireOutsideTransaction();
        var task = state.inspect(id);
        return state.copied(id, verifier().initial(task.target()));
    }

    public StorageSwitchState.Task stop(long id) {
        requireOutsideTransaction();
        return state.stop(id);
    }

    public StorageSwitchState.Task verifyFinal(long id) {
        requireOutsideTransaction();
        var task = state.inspect(id);
        return state.ready(id, verifier().finalSync(task.target()));
    }

    public StorageSwitchState.Task commit(long id) {
        requireOutsideTransaction();
        var task = state.inspect(id);
        if ("SWITCHED".equals(task.state())) return task;
        return state.commit(id, verifier().finalSync(task.target()));
    }

    public StorageSwitchState.Task abort(long id) {
        requireOutsideTransaction();
        return state.abort(id);
    }

    private StorageMigrationEvidence verifier() {
        var found = verifier.getIfAvailable();
        if (found == null) throw new IllegalStateException("Physical storage migration verifier is not configured");
        return found;
    }
    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Physical storage migration cannot join a platform transaction");
    }
}
