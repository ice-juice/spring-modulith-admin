package com.sma.admin.rule;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** JSON command records, kept independent of persistence, editor and engine implementation classes. */
public final class RuleCommands {
    private RuleCommands() {}
    public record Scene(String code, String name, RuleSchema inputSchema, RuleSchema.Field outputSchema,
            RuleSchema parameterPolicy, List<RuleDefinition.Kind> allowedKinds) {}
    public record Template(String code, String name, long sceneId, RuleTemplateDocument document) {}
    public record Instance(String code, String name, long templateVersionId, Map<String, Object> params,
            String objectType, String objectId, String environment, Instant startAt, Instant endAt) {}
    public record Save(long expectedRevision, Template template) {}
    public record SaveScene(long expectedRevision, Scene scene) {}
    public record SaveInstance(long expectedRevision, Instance instance) {}
    public record Publish(long expectedRevision, String operationKey) {}
    public record Rollback(long expectedRevision, long versionId, String operationKey) {}
    public record Simulate(RuleTemplateDocument document, Map<String, Object> input, Map<String, Object> params) {}
    public record Trial(long expectedRevision, Map<String, Object> input) {}
    public record Execute(Map<String, Object> input) {}
    public record Asset(long id, String kind, String code, String name, long revision, long releaseRevision,
            String state, Long activeVersionId, Object draft) {}
    public record Version(long id, long assetId, long revision, String contentHash, Long dependencyId, Object document,
            Long testRunId, String caseSetHash) {}
    public record CatalogEntry(String code, String name, Version version) {}
    public record Release(long id, long assetId, Long versionId, long releaseRevision, String action) {}
    public record PublicationLookup(String operationKey, Release release) {}
    public record Validation(boolean valid, List<RuleProblem> problems) {}
    public record Copy(String code, String name, Long versionId) {}
    public record Migration(long expectedRevision, long templateVersionId, Map<String, Object> params,
            List<Map<String, Object>> sampleInputs) {}
    public record Change(String path, String kind) {}
    public record VersionDiff(long assetId, long fromVersionId, long toVersionId, List<Change> changes, boolean truncated) {}
    public record Comparison(int sample, RuleExecutionApi.Result before, RuleExecutionApi.Result after, boolean changed) {}
    public record Preview(long instanceId, long baseRevision, boolean canMigrate, Instance proposed,
            List<Change> changes, List<Comparison> comparisons, List<RuleProblem> problems) {}
    public record CaseInput(String name, Map<String, Object> input, Map<String, Object> params,
            RuleExecutionApi.Status expectedStatus, Object expectedOutput, String expectedProblemCode,
            Boolean enabled, Instant evaluatedAt) {
        public CaseInput {
            expectedStatus = expectedStatus == null ? RuleExecutionApi.Status.SUCCESS : expectedStatus;
            enabled = enabled == null || enabled;
            evaluatedAt = evaluatedAt == null ? Instant.parse("2026-01-01T00:00:00Z") : evaluatedAt;
        }
    }
    public record CaseSave(long expectedRevision, CaseInput testcase) {}
    public record CaseView(long id, long templateId, long revision, CaseInput testcase) {}
    public record RunInput(long versionId, Long compareVersionId) {}
    public record CaseResult(long caseId, String name, RuleExecutionApi.Status actualStatus, boolean passed,
            Boolean changed, List<RuleProblem> problems) {}
    public record TestRun(long id, long templateId, Long versionId, String contentHash, Long compareVersionId,
            String compareHash, String caseSetHash, int total, boolean passed, List<CaseResult> results) {}
    public record MigrationItem(long instanceId, Migration migration) {}
    public record BatchMigration(String operationKey, List<MigrationItem> items) {}
    public record BatchItem(long instanceId, boolean passed, Long newRevision, String error) {}
    public record BatchReport(long id, String state, int total, boolean allPassed, List<BatchItem> results,
            List<Long> pendingInstanceIds) {}
    public record BatchLookup(String operationKey, BatchReport report) {}
    public record ExecutionDetail(String id, long instanceId, Long instanceRevision, Long releaseRevision,
            Long templateVersionId, String status, String contentHash, long durationMicros, Instant createdAt,
            List<RuleTrace> trace, List<RuleProblem> problems) {}
    public record ExecutionSearch(Long instanceId, Long templateId, Long templateVersionId, RuleExecutionApi.Status status,
            Instant from, Instant until, int page, int size) {}
    public record ExecutionSummary(String id, long instanceId, Long instanceRevision, Long releaseRevision,
            Long templateVersionId, String status, long durationMicros, Instant createdAt) {}
    public record Retention(int days, int limit) {}
    public record Cleanup(int executions, int testRuns) {}
    public record ImpactAsset(long id, String kind, String code, String state) {}
    public record DependencyImpact(long templateId, List<ImpactAsset> assets, int scanned, boolean truncated) {}
}
