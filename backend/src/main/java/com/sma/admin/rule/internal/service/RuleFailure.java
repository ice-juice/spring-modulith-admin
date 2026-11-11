package com.sma.admin.rule.internal.service;

import com.sma.admin.rule.RuleProblem;

final class RuleFailure extends RuntimeException {
    private final RuleProblem problem;
    RuleFailure(String path, String code) { this(new RuleProblem(path, code, null, null)); }
    RuleFailure(RuleProblem problem) { super(problem.code()); this.problem = problem; }
    RuleProblem problem() { return problem; }
}
