package com.sma.admin.common.internal.service;

import com.sma.admin.common.request.BusinessLedger;
import com.sma.admin.common.request.BusinessLedgerProvider;
import com.sma.admin.common.request.RequestCommand;
import com.sma.admin.common.request.RequestExecution;
import com.sma.admin.common.request.RequestReceipt;
import com.sma.admin.common.security.ActorContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.atomic.AtomicBoolean;

/** Never selects the platform ledger when a business resource has no binding. */
@Service
public class BusinessLedgerService implements BusinessLedger {
    private final ObjectProvider<BusinessLedgerProvider> providers;

    public BusinessLedgerService(ObjectProvider<BusinessLedgerProvider> providers) {
        this.providers = providers;
    }

    @Override public Session open(String storageGroup) {
        if (storageGroup == null || !storageGroup.matches("[a-z][a-z0-9-]{1,63}"))
            throw new IllegalArgumentException("Invalid business storage group");
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Business ledger resource must be selected before starting a transaction");
        long tenantId = ActorContext.tenantId();
        var provider = providers.getIfAvailable();
        if (provider == null) throw new IllegalStateException("Business ledger resource is not configured");
        var session = provider.open(tenantId, storageGroup);
        if (session == null) throw new IllegalStateException("Business ledger resource is not bound");
        return new Session() {
            private final AtomicBoolean closed = new AtomicBoolean();
            private void requireOpen() {
                if (closed.get()) throw new IllegalStateException("Business ledger session is closed");
                ActorContext.requireTenant(tenantId);
            }
            @Override public com.sma.admin.common.persistence.DataSourceIdentity resourceIdentity() {
                requireOpen(); return session.resourceIdentity();
            }
            @Override public <T> RequestExecution.Outcome<T> executeLocal(RequestCommand command, RequestExecution.Work<T> work) {
                requireOpen();
                return session.executeLocal(command, work);
            }
            @Override public RequestReceipt receipt(String operationId) {
                requireOpen();
                return session.receipt(operationId);
            }
            @Override public RequestReceipt receiptByKey(String key) {
                requireOpen();
                return session.receiptByKey(key);
            }
            @Override public void invalidate(String region) {
                requireOpen();
                if (!TransactionSynchronizationManager.isActualTransactionActive()
                        || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                    throw new IllegalStateException("Business cache invalidation requires a writable resource transaction");
                session.invalidate(region);
            }
            @Override public long authoritativeRevision(String region) {
                requireOpen();
                return session.authoritativeRevision(region);
            }
            @Override public long enqueue(String eventType, String eventKey, byte[] payload) {
                requireOpen();
                return session.enqueue(eventType, eventKey, payload);
            }
            @Override public <T> T query(java.util.function.Supplier<T> read) {
                requireOpen();
                return session.query(read);
            }
            @Override public void close() {
                if (closed.compareAndSet(false, true)) session.close();
            }
        };
    }
}
