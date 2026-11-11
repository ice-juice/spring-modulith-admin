package com.sma.admin.rule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Versioned, closed-by-default contract. Field names are stable identifiers, never Java member paths. */
public record RuleSchema(Map<String, Field> fields, Boolean additionalProperties) {
    public RuleSchema {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        additionalProperties = Boolean.TRUE.equals(additionalProperties);
    }
    public enum Type { STRING, BOOLEAN, INTEGER, DECIMAL, INSTANT, OBJECT, LIST }
    public record Field(Type type, Boolean required, BigDecimal minimum, BigDecimal maximum, Integer scale,
            Integer maxLength, Integer maxItems, List<Object> enumValues, Boolean sensitive,
            Map<String, Field> properties, Field items, Object defaultValue) {
        public Field {
            required = Boolean.TRUE.equals(required); sensitive = Boolean.TRUE.equals(sensitive);
            properties = properties == null ? null : Map.copyOf(properties);
            enumValues = enumValues == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(enumValues));
        }
        public static Field required(Type type) {
            return new Field(type, true, null, null, null, null, null, null, false, null, null, null);
        }
    }
    public static RuleSchema empty() { return new RuleSchema(Map.of(), false); }
}
