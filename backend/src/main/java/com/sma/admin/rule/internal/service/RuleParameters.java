package com.sma.admin.rule.internal.service;

import java.util.HashSet;
import java.util.Map;
import com.sma.admin.rule.RuleSchema;
import com.sma.admin.rule.RuleTemplateDocument;

final class RuleParameters {
    private RuleParameters() {}
    static void validate(RuleTemplateDocument doc) {
        if (doc.intervalConstraints() == null) return;
        if (doc.intervalConstraints().size() > 16) throw new RuleFailure("parameterConstraints", "CONSTRAINT_LIMIT");
        var ids = new HashSet<String>();
        for (var constraint : doc.intervalConstraints()) {
            if (constraint == null || constraint.id() == null || !constraint.id().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
                    || !ids.add(constraint.id()) || constraint.parameter() == null || constraint.lowerField() == null || constraint.upperField() == null)
                throw new RuleFailure("parameterConstraints", "INVALID_CONSTRAINT");
            var field = doc.parameterSchema().fields().get(constraint.parameter());
            if (field == null || field.type() != RuleSchema.Type.LIST || field.items() == null || field.items().type() != RuleSchema.Type.OBJECT)
                throw new RuleFailure(constraint.id(), "INTERVAL_LIST_REQUIRED");
            var properties = field.items().properties();
            if (!properties.containsKey(constraint.lowerField()) || !properties.containsKey(constraint.upperField())
                    || !java.util.Set.of(RuleSchema.Type.DECIMAL, RuleSchema.Type.INTEGER).contains(properties.get(constraint.lowerField()).type())
                    || !java.util.Set.of(RuleSchema.Type.DECIMAL, RuleSchema.Type.INTEGER).contains(properties.get(constraint.upperField()).type()))
                throw new RuleFailure(constraint.id(), "INVALID_INTERVAL_FIELDS");
            if (constraint.firstLower() != null) RuleValues.decimal(constraint.firstLower(), constraint.id());
        }
    }
    static Map<String, Object> normalize(RuleTemplateDocument doc, Map<String, Object> input) {
        var normalized = RuleValues.normalize(doc.parameterSchema(), input, "params");
        if (doc.intervalConstraints() != null) for (var constraint : doc.intervalConstraints())
            RuleIntervals.validate(normalized.get(constraint.parameter()), constraint.lowerField(), constraint.upperField(),
                    Boolean.TRUE.equals(constraint.contiguous()), constraint.firstLower());
        return normalized;
    }
}
