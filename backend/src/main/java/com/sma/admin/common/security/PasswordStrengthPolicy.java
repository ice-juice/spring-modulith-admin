package com.sma.admin.common.security;

/** Host login-password write policy. Verification of existing hashes must not call this policy. */
public interface PasswordStrengthPolicy {
    enum Decision { ACCEPTED, INVALID_FORMAT, WEAK }
    /** Inputs are transient sensitive values, never log or retain them. Does not authenticate an account. */
    Decision evaluate(String identifier,String candidate);
}
