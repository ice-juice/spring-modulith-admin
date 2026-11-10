package com.sma.extension.protocol;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** No polymorphic deserialization, unknown fields, duplicate keys or trailing documents. */
public final class ProtocolJson {
    public static final int MAX_DOCUMENT_BYTES = 1_048_576;
    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(262_144).maxNumberLength(100).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private ProtocolJson() {}
    public static byte[] write(Object value) {
        byte[] bytes = MAPPER.writeValueAsBytes(value);
        Checks.require(bytes.length <= MAX_DOCUMENT_BYTES, "document-too-large");
        return bytes;
    }
    public static <T> T read(byte[] bytes, Class<T> type) {
        Checks.require(bytes != null && bytes.length > 0 && bytes.length <= MAX_DOCUMENT_BYTES, "invalid-document-size");
        try { return MAPPER.readValue(bytes, type); }
        catch (RuntimeException failure) { throw new ProtocolException("invalid-document", failure); }
    }
    public static void validateValue(byte[] bytes) { read(bytes, Object.class); }
}
