package com.sma.extension.api;

/** One independently managed instance per immutable plugin version. Initialization must have no business side effects. */
public interface BusinessPlugin extends AutoCloseable {
    default void initialize(PluginContext context) throws Exception {}
    PluginResult execute(PluginCall call) throws Exception;
    @Override default void close() throws Exception {}
}
