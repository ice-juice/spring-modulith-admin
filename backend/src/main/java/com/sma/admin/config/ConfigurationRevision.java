package com.sma.admin.config;

/** Zero denotes no persisted layer; positive revisions identify immutable platform or tenant state. */
public record ConfigurationRevision(long platform, long tenant) {
    public ConfigurationRevision {
        if (platform < 0 || tenant < 0) throw new IllegalArgumentException("Configuration revisions cannot be negative");
    }
}
