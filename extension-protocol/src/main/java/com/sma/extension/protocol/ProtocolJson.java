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
        byte[] bytes = MAPPER.writeValueAsBytes(canonicalValue(value));
        Checks.require(bytes.length <= MAX_DOCUMENT_BYTES, "document-too-large");
        return bytes;
    }
    public static <T> T read(byte[] bytes, Class<T> type) {
        Checks.require(bytes != null && bytes.length > 0 && bytes.length <= MAX_DOCUMENT_BYTES, "invalid-document-size");
        try { return MAPPER.readValue(bytes, type); }
        catch (RuntimeException failure) { throw new ProtocolException("invalid-document", failure); }
    }
    public static void validateValue(byte[] bytes) { read(bytes, Object.class); }
    /** Stable across JVMs: record/object keys are sorted, ordered arrays retain order, sets sort by canonical JSON. */
    private static Object canonicalValue(Object value) {
        if(value==null || value instanceof byte[] || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?> || value instanceof java.time.temporal.TemporalAccessor) return value;
        if(value instanceof java.util.Map<?,?> map) {
            var result=new java.util.TreeMap<String,Object>();
            map.forEach((key,item) -> { Checks.require(key instanceof String,"non-string-json-key");result.put((String)key,canonicalValue(item)); });
            return result;
        }
        if(value instanceof java.util.Set<?> set) {
            return set.stream().map(ProtocolJson::canonicalValue).sorted(java.util.Comparator.comparing(MAPPER::writeValueAsString)).toList();
        }
        if(value instanceof java.util.Collection<?> collection) return collection.stream().map(ProtocolJson::canonicalValue).toList();
        if(value.getClass().isArray()) {
            var result=new java.util.ArrayList<Object>();
            for(int index=0;index<java.lang.reflect.Array.getLength(value);index++) result.add(canonicalValue(java.lang.reflect.Array.get(value,index)));
            return result;
        }
        if(value.getClass().isRecord()) {
            var result=new java.util.TreeMap<String,Object>();
            for(var component:value.getClass().getRecordComponents()) {
                try {
                    var accessor=component.getAccessor();
                    if(!accessor.canAccess(value)) Checks.require(accessor.trySetAccessible(),"inaccessible-protocol-record");
                    result.put(component.getName(),canonicalValue(accessor.invoke(value)));
                } catch(ReflectiveOperationException failure) { throw new ProtocolException("invalid-protocol-record",failure); }
            }
            return result;
        }
        throw new ProtocolException("unsupported-protocol-value");
    }
}
