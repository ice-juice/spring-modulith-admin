package com.sma.extension.api;

/** Only manifest-declared metric names are accepted by the host. No secrets or arbitrary labels. */
@FunctionalInterface
public interface PluginTelemetry {
    void increment(String metric, long amount);
}
