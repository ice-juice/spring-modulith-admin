package com.sma.admin.rule;

/** Safe diagnostics: identifies source locations without persisting input values or arbitrary exception text. */
public record RuleProblem(String path, String code, Integer line, Integer column) {}
