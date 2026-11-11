package com.sma.admin.common.request;

import java.util.Map;

/** Implemented by the owning module, without exposing its Mapper or entity to common. */
public interface RequestAuthorizer {
    void authorizeRequest(String method, Map<String, String> pathVariables);
    /** NATIVE adapters only: validate the actual protocol route before classifying a tunneled read.
     * Must not consume request bodies, accept client "safe" flags, or mutate invocation arguments. */
    default boolean readOnlyRequest(String method, Object[] arguments) { return false; }
    /** NATIVE adapters only: bounded validated resource identifiers, never credentials or body data. */
    default Map<String,String> requestResources(String method, Object[] arguments) { return Map.of(); }
}
