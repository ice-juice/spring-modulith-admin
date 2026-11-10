package com.sma.extension.api;

import java.util.Objects;

/** A JSON document, not an instance of a private plugin class. */
public record PluginResult(byte[] json) {
    public PluginResult { json = Objects.requireNonNull(json).clone(); }
    @Override public byte[] json() { return json.clone(); }
}
