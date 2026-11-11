package com.sma.admin.common.request;

import java.time.Duration;

public interface RequestExecution {
    /** Joins only the host's local database transaction. No network or independently committed writes in Work. */
    <T> Outcome<T> executeLocal(RequestCommand command, Work<T> work);
    RequestReceipt receipt(String operationId);
    /** For an interrupted response: the original random key is known before the first request. */
    RequestReceipt receiptByKey(String key);
    /** Reserves budget independently so a later business rollback does not refund it. */
    void claimBudget(String operation, int limit, Duration window);
    void claimTenantBudget(String operation, int limit, Duration window);
    interface Work<T> {
        /** Authorize execution/receipt scope before any result. Actor-owned metadata may outlive a deleted object;
         * business content and mutations still require current object authorization in the owning service. */
        void authorize();
        T execute();
    }
    record Outcome<T>(RequestReceipt receipt, boolean replayed, T result) {}
}
