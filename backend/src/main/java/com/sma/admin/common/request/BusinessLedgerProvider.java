package com.sma.admin.common.request;

/** Host storage runtime supplies the resource bound to a trusted tenant and business group. */
public interface BusinessLedgerProvider {
    BusinessLedger.Session open(long tenantId, String storageGroup);
}
