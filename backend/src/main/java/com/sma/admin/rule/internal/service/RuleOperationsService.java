package com.sma.admin.rule.internal.service;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.rule.*;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.internal.persistence.*;

@Service
@Transactional(readOnly = true)
public class RuleOperationsService {
    private final RuleService rules;
    private final ExecutionMapper executions;
    private final TestRunMapper runs;
    private final RuleJson json;
    private final ExpressionEngine expressions;
    public RuleOperationsService(RuleService rules, ExecutionMapper executions, TestRunMapper runs, RuleJson json, ExpressionEngine expressions) {
        this.rules = rules; this.executions = executions; this.runs = runs; this.json = json; this.expressions = expressions;
    }
    public ExecutionDetail detail(long instanceId, long executionId) {
        rules.get(instanceId, "INSTANCE");
        var row = executions.selectById(executionId);
        if (row == null || !row.getInstanceId().equals(instanceId) || !row.getPortal().equals(CurrentUser.portal()))
            throw new BizException(RuleErrorCode.NOT_FOUND);
        return new ExecutionDetail(row.getId().toString(), instanceId, row.getInstanceRevision(), row.getReleaseRevision(),
                row.getTemplateVersionId(), row.getStatus(), row.getContentHash(), row.getDurationMicros(), row.getCreatedAt(),
                List.of(json.readReport(row.getTraceJson(), RuleTrace[].class)), List.of(json.read(row.getProblemsJson(), RuleProblem[].class)));
    }
    public List<Map<String, Object>> statistics(long instanceId, Instant from, Instant until) {
        rules.get(instanceId, "INSTANCE");
        window(from, until);
        return executions.statistics(CurrentUser.tenantId(), CurrentUser.portal(), instanceId, from, until);
    }
    public List<ExecutionSummary> search(ExecutionSearch query) {
        if (query == null || query.page() < 1 || query.page() > 10000 || query.size() < 1 || query.size() > 100)
            throw new BizException(RuleErrorCode.INVALID, "INVALID_PAGE");
        window(query.from(), query.until());
        if (query.instanceId() != null) rules.get(query.instanceId(), "INSTANCE");
        if (query.templateId() != null) rules.get(query.templateId(), "TEMPLATE");
        Long owner = CurrentUser.optional().filter(user -> !user.superAdmin() && !user.dataAccess().all())
                .map(user -> user.dataAccess().self() ? user.userId() : -1L).orElse(null);
        return executions.search(CurrentUser.tenantId(), CurrentUser.portal(), owner, query, (query.page() - 1) * query.size()).stream()
                .map(row -> new ExecutionSummary(row.getId().toString(), row.getInstanceId(), row.getInstanceRevision(), row.getReleaseRevision(),
                        row.getTemplateVersionId(), row.getStatus(), row.getDurationMicros(), row.getCreatedAt())).toList();
    }
    private void window(Instant from, Instant until) {
        if (from == null || until == null || !from.isBefore(until) || Duration.between(from, until).compareTo(Duration.ofDays(31)) > 0)
            throw new BizException(RuleErrorCode.INVALID, "WINDOW_MAX_31_DAYS");
    }
    public Map<String, Integer> resources() {
        allScope();
        int expressionsUsed = expressions.tenantCacheSize(); int plansUsed = RuleFlowRuntime.tenantSize();
        return Map.of("expressions", expressionsUsed, "expressionTenantLimit", 64, "expressionAtLimit", expressionsUsed >= 64 ? 1 : 0,
                "plans", plansUsed, "planTenantLimit", 32, "planAtLimit", plansUsed >= 32 ? 1 : 0);
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.SUPPORTS,readOnly=true)
    public Map<String, Integer> clearResources() {
        allScope();
        return Map.of("expressionsCleared", expressions.clearTenantCache(), "idlePlansCleared", RuleFlowRuntime.clearTenantIdle());
    }
    @Transactional public Cleanup cleanup(Retention command) {
        allScope();
        if (command == null || command.days() < 7 || command.days() > 3650 || command.limit() < 1 || command.limit() > 500)
            throw new BizException(RuleErrorCode.INVALID, "RETENTION_MIN_7_DAYS_LIMIT_500");
        Instant cutoff = Instant.now().minus(Duration.ofDays(command.days()));
        return new Cleanup(executions.purge(CurrentUser.tenantId(), CurrentUser.portal(), cutoff, command.limit()),
                runs.purgeUnreferenced(CurrentUser.tenantId(), CurrentUser.portal(), cutoff, command.limit()));
    }
    private void allScope() {
        var user = CurrentUser.optional().orElseThrow(() -> new AccessDeniedException("Rule identity required"));
        if (!user.superAdmin() && !user.dataAccess().all()) throw new AccessDeniedException("Rule ALL scope required");
    }
}
