package com.sma.admin.rule;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface RuleExecutionApi {
    Result execute(Request request);

    record Request(long instanceId, Map<String, Object> input) {}
    enum Status { SUCCESS, NOT_MATCHED, INVALID_INPUT, NOT_CONFIGURED, DISABLED, NOT_EFFECTIVE, TIMEOUT, ERROR }
    record Result(String executionId, Status status, Object output, Long instanceRevision, Long templateVersionId,
            String contentHash, Instant evaluatedAt, List<RuleTrace> trace, List<RuleProblem> problems, Long releaseRevision) {
        public Result(String executionId, Status status, Object output, Long instanceRevision, Long templateVersionId,
                String contentHash, Instant evaluatedAt, List<RuleTrace> trace, List<RuleProblem> problems) {
            this(executionId, status, output, instanceRevision, templateVersionId, contentHash, evaluatedAt, trace, problems, null);
        }
    }
}
