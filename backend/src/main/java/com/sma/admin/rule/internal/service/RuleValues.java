package com.sma.admin.rule.internal.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sma.admin.rule.RuleSchema;

/** Normalizes JSON numbers into decimals and makes a fresh immutable graph for every invocation. */
public final class RuleValues {
    private RuleValues() {}
    public static Map<String, Object> normalize(RuleSchema schema, Map<String, Object> values, String path) {
        if (schema == null) throw new RuleFailure(path, "SCHEMA_REQUIRED");
        graph(values, 0, new int[]{4096}, path);
        validateSchema(schema.fields(), 0);
        return object(schema.fields(), schema.additionalProperties(), values == null ? Map.of() : values, path, 0);
    }
    public static Object output(RuleSchema.Field schema, Object value) {
        graph(value, 0, new int[]{4096}, "output");
        validateSchema(Map.of("output", schema), 0);
        if (value == null) {
            if (schema.required()) throw new RuleFailure("output", "REQUIRED");
            return null;
        }
        return field(schema, value, "output", 0);
    }
    public static void validateSchema(Map<String, RuleSchema.Field> fields, int depth) {
        validateSchema(fields, depth, new int[]{4096});
    }
    private static void validateSchema(Map<String, RuleSchema.Field> fields, int depth, int[] budget) {
        if (fields == null || fields.size() > 100 || depth > 8) throw new RuleFailure("schema", "SCHEMA_LIMIT");
        fields.forEach((key, field) -> {
            if (--budget[0] < 0) throw new RuleFailure("schema", "SCHEMA_LIMIT");
            if (!key.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || field == null || field.type() == null)
                throw new RuleFailure("schema." + key, "INVALID_FIELD");
            if (field.minimum() != null && field.maximum() != null && field.minimum().compareTo(field.maximum()) > 0)
                throw new RuleFailure("schema." + key, "INVALID_RANGE");
            if (field.minimum() != null) decimal(field.minimum(), "schema." + key);
            if (field.maximum() != null) decimal(field.maximum(), "schema." + key);
            if (field.enumValues() != null) {
                if (field.enumValues().size() > 256) throw new RuleFailure("schema." + key, "SCHEMA_LIMIT");
                field.enumValues().forEach(item -> freeze(item, depth));
            }
            if (field.scale() != null && (field.scale() < 0 || field.scale() > 18)
                    || field.maxLength() != null && (field.maxLength() < 0 || field.maxLength() > 4096)
                    || field.maxItems() != null && (field.maxItems() < 0 || field.maxItems() > 256))
                throw new RuleFailure("schema." + key, "SCHEMA_LIMIT");
            if (field.type() == RuleSchema.Type.OBJECT) validateSchema(field.properties(), depth + 1, budget);
            if (field.type() == RuleSchema.Type.LIST) {
                if (field.items() == null) throw new RuleFailure("schema." + key, "ITEM_SCHEMA_REQUIRED");
                validateSchema(Map.of("item", field.items()), depth + 1, budget);
            }
            if (field.defaultValue() != null) {
                graph(field.defaultValue(), depth, new int[]{4096}, "schema." + key + ".default");
                field(field, field.defaultValue(), "schema." + key + ".default", depth);
            }
        });
    }
    private static Map<String, Object> object(Map<String, RuleSchema.Field> fields, boolean additional,
            Map<?, ?> values, String path, int depth) {
        if (depth > 10 || values.size() > 100) throw new RuleFailure(path, "INPUT_LIMIT");
        var result = new LinkedHashMap<String, Object>();
        for (var entry : fields.entrySet()) {
            String key = entry.getKey(); var field = entry.getValue();
            Object value = values.containsKey(key) ? values.get(key) : field.defaultValue();
            if (value == null && field.required()) throw new RuleFailure(path + "." + key, "REQUIRED");
            result.put(key, value == null ? null : field(field, value, path + "." + key, depth + 1));
        }
        for (var key : values.keySet()) {
            if (!(key instanceof String text)) throw new RuleFailure(path, "INVALID_KEY");
            if (!fields.containsKey(text)) {
                if (!additional) throw new RuleFailure(path + "." + text, "UNKNOWN_FIELD");
                result.put(text, freeze(values.get(key), depth + 1));
            }
        }
        return Collections.unmodifiableMap(result);
    }
    private static Object field(RuleSchema.Field spec, Object value, String path, int depth) {
        Object normalized = switch (spec.type()) {
            case STRING -> {
                if (!(value instanceof String text) || text.length() > (spec.maxLength() == null ? 1024 : spec.maxLength()))
                    throw new RuleFailure(path, "INVALID_STRING");
                yield text;
            }
            case BOOLEAN -> { if (!(value instanceof Boolean)) throw new RuleFailure(path, "INVALID_BOOLEAN"); yield value; }
            case DECIMAL, INTEGER -> {
                if (!(value instanceof Number) || value instanceof Float || value instanceof Double)
                    throw new RuleFailure(path, "DECIMAL_REQUIRES_EXACT_NUMBER");
                BigDecimal number = decimal(value, path);
                if (spec.type() == RuleSchema.Type.INTEGER && number.stripTrailingZeros().scale() > 0
                        || spec.scale() != null && number.stripTrailingZeros().scale() > spec.scale()
                        || spec.minimum() != null && number.compareTo(spec.minimum()) < 0
                        || spec.maximum() != null && number.compareTo(spec.maximum()) > 0)
                    throw new RuleFailure(path, "NUMBER_OUT_OF_RANGE");
                yield number;
            }
            case INSTANT -> {
                if (!(value instanceof String text)) throw new RuleFailure(path, "INVALID_INSTANT");
                try { yield Instant.parse(text).toString(); }
                catch (DateTimeParseException ex) { throw new RuleFailure(path, "INVALID_INSTANT"); }
            }
            case OBJECT -> {
                if (!(value instanceof Map<?, ?> map)) throw new RuleFailure(path, "INVALID_OBJECT");
                yield object(spec.properties(), false, map, path, depth);
            }
            case LIST -> {
                if (!(value instanceof List<?> list) || list.size() > (spec.maxItems() == null ? 100 : spec.maxItems()))
                    throw new RuleFailure(path, "INVALID_LIST");
                var normalizedList = new ArrayList<Object>();
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item == null) throw new RuleFailure(path + "[" + i + "]", "NULL_ITEM");
                    normalizedList.add(field(spec.items(), item, path + "[" + i + "]", depth + 1));
                }
                yield Collections.unmodifiableList(normalizedList);
            }
        };
        if (spec.enumValues() != null && !spec.enumValues().isEmpty()
                && spec.enumValues().stream().noneMatch(candidate -> equal(normalized, candidate)))
            throw new RuleFailure(path, "NOT_IN_ENUM");
        return normalized;
    }
    static BigDecimal decimal(Object value, String path) {
        try {
            var number = new BigDecimal(value.toString());
            if (number.precision() > 32 || Math.abs(number.scale()) > 18) throw new RuleFailure(path, "NUMBER_LIMIT");
            return number;
        } catch (NumberFormatException ex) { throw new RuleFailure(path, "INVALID_NUMBER"); }
    }
    static boolean equal(Object a, Object b) {
        return a instanceof Number && b instanceof Number ? decimal(a, "value").compareTo(decimal(b, "value")) == 0
                : java.util.Objects.equals(a, b);
    }
    public static Object freeze(Object value, int depth) {
        return freeze(value, depth, new int[]{4096});
    }
    private static Object freeze(Object value, int depth, int[] budget) {
        if (--budget[0] < 0) throw new RuleFailure("value", "VALUE_LIMIT");
        if (depth > 10) throw new RuleFailure("value", "VALUE_LIMIT");
        if (value == null || value instanceof Boolean) return value;
        if (value instanceof Number) return decimal(value, "value");
        if (value instanceof String text && text.length() <= 4096) return text;
        if (value instanceof Map<?, ?> map && map.size() <= 100) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text) || text.length() > 64) throw new RuleFailure("value", "INVALID_KEY");
                result.put(text, freeze(item, depth + 1, budget));
            });
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list && list.size() <= 256) {
            var result = new ArrayList<Object>();
            list.forEach(item -> result.add(freeze(item, depth + 1, budget)));
            return Collections.unmodifiableList(result);
        }
        throw new RuleFailure("value", "VALUE_LIMIT");
    }
    private static void graph(Object value, int depth, int[] budget, String path) {
        if (--budget[0] < 0 || depth > 10) throw new RuleFailure(path, "VALUE_LIMIT");
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 100) throw new RuleFailure(path, "VALUE_LIMIT");
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.length() > 64) throw new RuleFailure(path, "INVALID_KEY");
                graph(entry.getValue(), depth + 1, budget, path);
            }
        } else if (value instanceof List<?> list) {
            if (list.size() > 256) throw new RuleFailure(path, "VALUE_LIMIT");
            for (Object item : list) graph(item, depth + 1, budget, path);
        } else if (value != null && !(value instanceof Number) && !(value instanceof Boolean) && !(value instanceof String))
            throw new RuleFailure(path, "INVALID_VALUE");
    }
}
