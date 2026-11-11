package com.sma.admin.rule.internal.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Deterministic, bounded half-open interval validation and lookup, reusable outside discount scenarios. */
final class RuleIntervals {
    private RuleIntervals() {}
    static void validate(Object raw, String lowerKey, String upperKey, boolean contiguous, BigDecimal initial) {
        if (!(raw instanceof List<?> rows) || rows.isEmpty() || rows.size() > 256) throw new RuleFailure("params", "INTERVAL_LIMIT");
        BigDecimal previous = null;
        for (int i = 0; i < rows.size(); i++) {
            if (!(rows.get(i) instanceof Map<?, ?> row) || !(row.get(lowerKey) instanceof Number))
                throw new RuleFailure("params", "INVALID_INTERVAL");
            var lower = RuleValues.decimal(row.get(lowerKey), "params"); Object high = row.get(upperKey);
            if (high != null && !(high instanceof Number)) throw new RuleFailure("params", "INVALID_INTERVAL");
            var upper = high == null ? null : RuleValues.decimal(high, "params");
            if (upper != null && lower.compareTo(upper) >= 0 || upper == null && i != rows.size() - 1
                    || i == 0 && initial != null && lower.compareTo(initial) != 0
                    || i > 0 && (previous == null || lower.compareTo(previous) < 0 || contiguous && lower.compareTo(previous) != 0))
                throw new RuleFailure("params", "INTERVAL_OVERLAP_OR_GAP");
            previous = upper;
        }
    }
    static Object lookup(Object rows, Object point, Object lowerName, Object upperName, Object outputName, Object fallback) {
        if (!(lowerName instanceof String lower) || !(upperName instanceof String upper) || !(outputName instanceof String field)
                || !lower.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || !upper.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
                || !field.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || !(point instanceof Number))
            throw new RuleFailure("expression", "INVALID_INTERVAL_ARGUMENT");
        validate(rows, lower, upper, false, null); BigDecimal value = RuleValues.decimal(point, "expression");
        for (Object raw : (List<?>) rows) {
            var row = (Map<?, ?>) raw;
            if (value.compareTo(RuleValues.decimal(row.get(lower), "expression")) >= 0
                    && (row.get(upper) == null || value.compareTo(RuleValues.decimal(row.get(upper), "expression")) < 0)) {
                if (!row.containsKey(field)) throw new RuleFailure("expression", "INTERVAL_RESULT_MISSING");
                return row.get(field);
            }
        }
        return fallback;
    }
}
