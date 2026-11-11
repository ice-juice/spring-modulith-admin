package com.sma.admin.iam;
/** Own-account writes only; requires an existing write transaction and a human principal. */
public interface AccountCredentialGuard {
    void lockOwnAccount();
    /** Latest committed password under the same lock; not a general probing API. */
    boolean matchesOwnCurrentPassword(String candidate);
}
