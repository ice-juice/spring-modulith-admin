package com.sma.admin.common.security;

/** Deployment-owned platform tenant for host startup work. Never derived from an HTTP request. */
public interface PlatformTenantQuery {
    long platformTenantId();
}
