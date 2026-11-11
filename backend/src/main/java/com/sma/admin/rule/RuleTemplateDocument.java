package com.sma.admin.rule;

import java.util.List;
import java.util.Map;

/** One immutable candidate contains executable logic, parameter contract and validation examples together. */
public record RuleTemplateDocument(int formatVersion, RuleSchema inputSchema, RuleSchema parameterSchema,
        RuleSchema.Field outputSchema, RuleDefinition definition, Map<String, ParameterUi> parameterUi,
        List<Example> examples, List<IntervalConstraint> intervalConstraints) {
    public RuleTemplateDocument(int formatVersion, RuleSchema inputSchema, RuleSchema parameterSchema,
            RuleSchema.Field outputSchema, RuleDefinition definition, Map<String, ParameterUi> parameterUi, List<Example> examples) {
        this(formatVersion, inputSchema, parameterSchema, outputSchema, definition, parameterUi, examples, null);
    }
    public record ParameterUi(String label, String help, String group, int order) {}
    public record Example(String name, Map<String, Object> input, Map<String, Object> params, Object expectedOutput) {}
    public record IntervalConstraint(String id, String parameter, String lowerField, String upperField,
            Boolean contiguous, java.math.BigDecimal firstLower) {}
}
