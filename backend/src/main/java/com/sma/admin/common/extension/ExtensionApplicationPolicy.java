package com.sma.admin.common.extension;

import java.util.Set;

/** Business-owned allowlist, evaluated for the authenticated OpenAPI application on every call. Empty denies all. */
public interface ExtensionApplicationPolicy {
    String pluginId();
    Set<String> permissions(String applicationId,long tenantId,String portal);
}
