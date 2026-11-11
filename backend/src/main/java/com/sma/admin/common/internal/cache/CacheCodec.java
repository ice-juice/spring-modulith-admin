package com.sma.admin.common.internal.cache;

import tools.jackson.databind.json.JsonMapper;

/** Explicit DTO decoding only; never enable type metadata or depend on web JSON settings. */
final class CacheCodec {
    private final JsonMapper json = JsonMapper.builder().build();
    byte[] encode(Object value) { return json.writeValueAsBytes(value); }
    <T> T decode(byte[] payload, Class<T> type) { return json.readValue(payload, type); }
}
