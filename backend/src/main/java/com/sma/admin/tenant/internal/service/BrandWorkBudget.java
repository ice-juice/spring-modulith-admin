package com.sma.admin.tenant.internal.service;

import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sma.admin.common.*;

/** Bounds the lifetime of decoded snapshots across concurrent tenant transactions, not just individual decodes. */
@Component
public class BrandWorkBudget {
    private final Semaphore permits=new Semaphore(4);
    public void enter() {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Brand image work requires a transaction");
        if(TransactionSynchronizationManager.getSynchronizations().stream().anyMatch(sync->sync instanceof Lease lease && lease.owner()==this)) return;
        if(!permits.tryAcquire()) throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE);
        try { TransactionSynchronizationManager.registerSynchronization(new Lease()); }
        catch(RuntimeException failure) { permits.release();throw failure; }
    }
    private final class Lease implements TransactionSynchronization {
        private boolean released;
        BrandWorkBudget owner() { return BrandWorkBudget.this; }
        @Override public void afterCompletion(int status) { if(!released){released=true;permits.release();} }
    }
}
