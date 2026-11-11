package com.sma.admin.common.extension;

import com.sma.extension.api.InvocationIdentity;

/** Stable business module adapter. Implementations enforce their own authorization and transaction boundaries. */
public interface ExtensionCapability {
    String id();
    byte[] invoke(String pluginId, String version, String invocationId, InvocationIdentity identity, byte[] input) throws Exception;
}
