package com.sma.admin.tenant.storage;

/** A deployment-owned adapter; business modules must not implement or call this host SPI. */
public interface StorageCredentialResolver {
    Credentials resolve(String secretRef);

    record Credentials(String username, String password) {
        public Credentials {
            if (username == null || username.isBlank() || password == null || password.isBlank())
                throw new IllegalArgumentException("Invalid storage credentials");
        }
        @Override public String toString() { return "Credentials[redacted]"; }
    }
}
