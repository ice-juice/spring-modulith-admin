package com.sma.admin.common.security;

/**
 * Tenant/configuration-bound encryption; does not grant access to a configuration.
 * The owning module must authorize the operation and load the reference from its own persistence first.
 */
public interface TenantSecretCodec {
    boolean configured();
    String encrypt(TenantConfigurationRef reference, String purpose, char[] plaintext);
    SecretValue decrypt(TenantConfigurationRef reference, String purpose, String envelope);
    String reencrypt(TenantConfigurationRef reference, String purpose, String envelope);
    /** Keyed digest for low-entropy values. Never use an unkeyed hash for OTP-bearing requests. */
    String fingerprint(TenantConfigurationRef reference, String purpose, char[] plaintext);
    boolean matchesFingerprint(TenantConfigurationRef reference, String purpose, char[] plaintext, String fingerprint);
}
