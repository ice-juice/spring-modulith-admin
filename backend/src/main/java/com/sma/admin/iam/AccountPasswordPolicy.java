package com.sma.admin.iam;
/** Synchronous policy inside the IAM credential write transaction, before encoding or persistence. */
public interface AccountPasswordPolicy {
    /** Candidate is transient sensitive input; never log or return it. */
    void validate(long userId, String portal, String candidate);
}
