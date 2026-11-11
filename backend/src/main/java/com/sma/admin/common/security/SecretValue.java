package com.sma.admin.common.security;

import java.util.Arrays;
import com.fasterxml.jackson.annotation.JsonValue;

/** Deliberate, short-lived access for a transport adapter. Never put this value into a response DTO. */
public final class SecretValue implements AutoCloseable {
    private char[] characters;

    public SecretValue(char[] value) { characters = value.clone(); }

    /** Caller owns and must erase the returned copy after use. */
    public synchronized char[] copy() {
        if (characters == null) throw new IllegalStateException("Secret has been closed");
        return characters.clone();
    }

    @Override public synchronized void close() {
        if (characters != null) { Arrays.fill(characters, '\0'); characters = null; }
    }

    @Override @JsonValue public String toString() { return "[REDACTED]"; }
}
