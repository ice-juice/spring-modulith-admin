package com.sma.admin.config;

/** An effective value for the trusted current tenant; a null value remains distinct from an absent override. */
public record ParameterSnapshot(String key, ParameterDefinition.ValueType valueType, String value,
        ConfigurationOrigin origin, ConfigurationRevision revision) {
    @Override public String toString() { return "ParameterSnapshot[key=" + key + ", origin=" + origin + ", revision=" + revision + "]"; }
}
