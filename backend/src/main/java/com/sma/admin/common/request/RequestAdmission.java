package com.sma.admin.common.request;

/** Bounded admission without a wrapping transaction or a generic completion receipt.
 * Native protocols keep their own identity, transactions, reconciliation and retry semantics.
 */
public interface RequestAdmission {
    <T> T execute(RequestExecution.Work<T> work);
}
