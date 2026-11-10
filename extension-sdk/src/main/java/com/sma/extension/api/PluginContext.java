package com.sma.extension.api;

import java.util.Objects;

/** Host adapters enforce authorization again. This is a narrow API, not a JVM security sandbox. */
public record PluginContext(String pluginId, String version, CapabilityInvoker capabilities, PluginTelemetry telemetry) {
    public PluginContext {
        Objects.requireNonNull(pluginId); Objects.requireNonNull(version);
        Objects.requireNonNull(capabilities); Objects.requireNonNull(telemetry);
    }
}
