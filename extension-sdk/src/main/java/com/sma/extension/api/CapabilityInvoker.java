package com.sma.extension.api;

/** Valid only inside a managed invocation; no caller-selected identity or arbitrary URL/SQL interface. */
@FunctionalInterface
public interface CapabilityInvoker {
    byte[] invoke(String capabilityId, byte[] json) throws Exception;
}
