package com.sma.admin.rule.internal.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.rule.*;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.RuleExecutionApi.Status;
import com.sma.admin.rule.internal.persistence.*;

@Service @Transactional(readOnly = true)
public class RuleTestService {
    private final AssetMapper assets;
    private final VersionMapper versions;
    private final TestCaseMapper cases;
    private final TestRunMapper runs;
    private final WriteLockMapper locks;
    private final RuleEvaluator evaluator;
    private final RuleJson json;
    public RuleTestService(AssetMapper assets, VersionMapper versions, TestCaseMapper cases, TestRunMapper runs,
            WriteLockMapper locks, RuleEvaluator evaluator, RuleJson json) {
        this.assets = assets; this.versions = versions; this.cases = cases; this.runs = runs;
        this.locks = locks; this.evaluator = evaluator; this.json = json;
    }
    @Transactional public CaseView save(long templateId, Long caseId, CaseSave command) {
        locks.lock(CurrentUser.tenantId()); manage(templateId); var input = command.testcase();
        if (input == null || input.name() == null || input.name().isBlank() || input.name().length() > 128
                || !List.of(Status.SUCCESS, Status.NOT_MATCHED, Status.INVALID_INPUT, Status.ERROR, Status.TIMEOUT).contains(input.expectedStatus())
                || input.expectedProblemCode() != null && input.expectedProblemCode().length() > 128)
            throw new BizException(RuleErrorCode.INVALID, "INVALID_TEST_CASE");
        if (!normal(input.expectedStatus()) && input.expectedOutput() != null) throw new BizException(RuleErrorCode.INVALID, "UNEXPECTED_ERROR_OUTPUT");
        try { RuleValues.freeze(input.input(), 0); RuleValues.freeze(input.params(), 0); RuleValues.freeze(input.expectedOutput(), 0); }
        catch (RuleFailure ex) { throw new BizException(RuleErrorCode.INVALID, ex.problem().code()); }
        TestCaseEntity row;
        if (caseId == null) {
            if (cases.selectCount(new LambdaQueryWrapper<TestCaseEntity>().eq(TestCaseEntity::getTemplateId, templateId)) >= 100)
                throw new BizException(RuleErrorCode.INVALID, "CASE_LIMIT");
            row = new TestCaseEntity(); row.setPortal(CurrentUser.portal()); row.setTemplateId(templateId); row.setRevision(1L);
        } else {
            row = cases.selectById(caseId);
            if (row == null || row.getTemplateId() != templateId || !CurrentUser.portal().equals(row.getPortal())) throw new BizException(RuleErrorCode.NOT_FOUND);
            if (row.getRevision() != command.expectedRevision()) throw new BizException(RuleErrorCode.CONFLICT);
            row.setRevision(row.getRevision() + 1);
        }
        row.setEnabled(input.enabled()); row.setPayloadJson(json.write(input));
        if (caseId == null) cases.insert(row); else cases.updateById(row);
        return view(row);
    }
    public List<CaseView> list(long templateId) {
        manage(templateId);
        return cases.selectList(new LambdaQueryWrapper<TestCaseEntity>().eq(TestCaseEntity::getTemplateId, templateId)
                .eq(TestCaseEntity::getPortal, CurrentUser.portal()).orderByAsc(TestCaseEntity::getId).last("LIMIT 100"))
                .stream().map(this::view).toList();
    }
    @Transactional public TestRun run(long templateId, RunInput command) {
        manage(templateId); var version = version(templateId, command.versionId());
        var other = command.compareVersionId() == null ? null : version(templateId, command.compareVersionId());
        return perform(templateId, version.getId(), version.getContentHash(), json.read(version.getContentJson(), RuleTemplateDocument.class), other);
    }
    /** Failure reports survive a rejected publication; this transaction never acquires the parent's write lock. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TestRun verifyCandidate(long templateId, RuleTemplateDocument document, String contentHash) {
        manage(templateId); return perform(templateId, null, contentHash, document, null);
    }
    public List<TestRun> runs(long templateId, int limit) {
        manage(templateId); if (limit < 1 || limit > 100) throw new BizException(RuleErrorCode.INVALID, "INVALID_LIMIT");
        return runs.selectList(new LambdaQueryWrapper<TestRunEntity>().eq(TestRunEntity::getTemplateId, templateId)
                .eq(TestRunEntity::getPortal, CurrentUser.portal()).orderByDesc(TestRunEntity::getCreatedAt).orderByDesc(TestRunEntity::getId).last("LIMIT " + limit))
                .stream().map(row -> json.readReport(row.getReportJson(), TestRun.class)).toList();
    }
    private TestRun perform(long templateId, Long versionId, String hash, RuleTemplateDocument doc, VersionEntity compared) {
        var enabled = cases.selectList(new LambdaQueryWrapper<TestCaseEntity>().eq(TestCaseEntity::getTemplateId, templateId)
                .eq(TestCaseEntity::getPortal, CurrentUser.portal()).eq(TestCaseEntity::getEnabled, true)
                .orderByAsc(TestCaseEntity::getId).last("LIMIT 101"));
        if (enabled.size() > 100) throw new BizException(RuleErrorCode.INVALID, "CASE_LIMIT");
        var rows = new ArrayList<CaseResult>();
        var other = compared == null ? null : json.read(compared.getContentJson(), RuleTemplateDocument.class);
        if (doc.examples() == null || doc.examples().isEmpty() || doc.examples().size() > 50) {
            rows.add(new CaseResult(0, "examples", Status.ERROR, false, null,
                    List.of(new RuleProblem("examples", "EXAMPLES_REQUIRED", null, null))));
        } else {
            int index = 0;
            for (var example : doc.examples()) {
                if (example == null || example.name() == null || example.name().isBlank() || example.name().length() > 128) {
                    rows.add(new CaseResult(-1L - index++, "example", Status.ERROR, false, null,
                            List.of(new RuleProblem("examples", "INVALID_EXAMPLE", null, null)))); continue;
                }
                var input = new CaseInput(example.name(), example.input(), example.params(), null, example.expectedOutput(), null, true, null);
                var actual = evaluate(doc, input); boolean passed = normal(actual.status) && RuleEvaluator.deepEqual(actual.output, example.expectedOutput());
                Boolean changed = null;
                if (other != null) { var next = evaluate(other, input); changed = actual.status != next.status || !RuleEvaluator.deepEqual(actual.output, next.output); }
                var problems = new ArrayList<>(actual.problems);
                if (!passed) problems.add(new RuleProblem("examples", "ASSERTION_FAILED", null, null));
                rows.add(new CaseResult(-1L - index++, example.name(), actual.status, passed, changed, List.copyOf(problems)));
            }
        }
        for (var row : enabled) {
            var input = json.read(row.getPayloadJson(), CaseInput.class); var actual = evaluate(doc, input);
            boolean passed = actual.status == input.expectedStatus() && (!normal(actual.status) || RuleEvaluator.deepEqual(actual.output, input.expectedOutput()))
                    && (input.expectedProblemCode() == null || actual.problems.stream().anyMatch(problem -> problem.code().equals(input.expectedProblemCode())));
            Boolean changed = null;
            if (other != null) { var next = evaluate(other, input); changed = actual.status != next.status || !RuleEvaluator.deepEqual(actual.output, next.output); }
            var problems = new ArrayList<>(actual.problems);
            if (!passed) problems.add(new RuleProblem("testcase", "ASSERTION_FAILED", null, null));
            rows.add(new CaseResult(row.getId(), input.name(), actual.status, passed, changed, List.copyOf(problems)));
        }
        String caseHash = json.hash(json.write(enabled.stream().map(row -> json.hash(row.getId() + ":" + row.getRevision() + ":" + row.getPayloadJson())).toList()));
        var entity = new TestRunEntity(); entity.setPortal(CurrentUser.portal()); entity.setTemplateId(templateId); entity.setVersionId(versionId);
        entity.setContentHash(hash); entity.setCaseSetHash(caseHash); entity.setPassed(rows.stream().allMatch(CaseResult::passed));
        entity.setReportJson("{}"); runs.insert(entity);
        var report = new TestRun(entity.getId(), templateId, versionId, hash, compared == null ? null : compared.getId(),
                compared == null ? null : compared.getContentHash(), caseHash, rows.size(), entity.getPassed(), List.copyOf(rows));
        entity.setReportJson(json.writeReport(report)); runs.updateById(entity); return report;
    }
    private record Outcome(Status status, Object output, List<RuleProblem> problems) {}
    private Outcome evaluate(RuleTemplateDocument doc, CaseInput input) {
        try { var result = evaluator.evaluate(doc, input.input(), input.params(), input.evaluatedAt());
            return new Outcome(result.matched() ? Status.SUCCESS : Status.NOT_MATCHED, result.output(), List.of()); }
        catch (RuleFailure ex) { return new Outcome(ex.problem().code().contains("TIME_OUT") ? Status.TIMEOUT
                : ex.problem().path().startsWith("input") ? Status.INVALID_INPUT : Status.ERROR, null, List.of(ex.problem())); }
    }
    private static boolean normal(Status status) { return status == Status.SUCCESS || status == Status.NOT_MATCHED; }
    private void manage(long id) {
        var row = assets.selectById(id);
        if (row == null || !"TEMPLATE".equals(row.getKind()) || !CurrentUser.portal().equals(row.getPortal())) throw new BizException(RuleErrorCode.NOT_FOUND);
        CurrentUser.optional().ifPresent(user -> {
            if (!user.superAdmin() && !user.dataAccess().all() && !(user.dataAccess().self() && row.getCreatedBy().equals(user.userId())))
                throw new AccessDeniedException("Rule data scope denied");
        });
    }
    private VersionEntity version(long templateId, long id) {
        var row = versions.selectById(id);
        if (row == null || row.getAssetId() != templateId || !"TEMPLATE".equals(row.getKind()) || !CurrentUser.portal().equals(row.getPortal()))
            throw new BizException(RuleErrorCode.NOT_FOUND);
        if (!json.hash(row.getContentJson()).equals(row.getContentHash())) throw new BizException(RuleErrorCode.UNAVAILABLE);
        return row;
    }
    private CaseView view(TestCaseEntity row) { return new CaseView(row.getId(), row.getTemplateId(), row.getRevision(), json.read(row.getPayloadJson(), CaseInput.class)); }
}
