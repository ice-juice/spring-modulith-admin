package com.sma.admin.rule.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Dedicated exact-number decoder; does not change the application's existing JSON conventions. */
@Component
public class RuleJson {
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public <T> T read(String text, Class<T> type) {
        return read(text, type, 131072);
    }
    public <T> T readReport(String text, Class<T> type) { return read(text, type, 1048576); }
    private <T> T read(String text, Class<T> type, int limit) {
        if (text == null || text.length() > limit) throw new RuleFailure("document", "DOCUMENT_LIMIT");
        try {
            T value = mapper.readValue(text, type);
            if (value == null) throw new RuleFailure("document", "DOCUMENT_REQUIRED");
            return value;
        }
        catch (Exception ex) { throw new RuleFailure("document", "INVALID_JSON"); }
    }
    public String write(Object value) {
        return write(value, 131072);
    }
    public String writeReport(Object value) { return write(value, 1048576); }
    private String write(Object value, int limit) {
        try {
            String text = mapper.writeValueAsString(value);
            if (text.length() > limit) throw new RuleFailure("document", "DOCUMENT_LIMIT");
            return text;
        }
        catch (Exception ex) { throw new RuleFailure("document", "INVALID_DOCUMENT"); }
    }
    public String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
