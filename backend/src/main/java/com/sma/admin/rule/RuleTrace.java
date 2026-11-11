package com.sma.admin.rule;

/** Trace values are deliberately omitted; input values can contain secrets even in intermediate expressions. */
public record RuleTrace(String id, String kind, boolean evaluated, Integer line, Integer column, String outcome) {}
