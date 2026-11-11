package com.sma.admin.rule.internal.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.security.RuleBindingPolicy;
import com.sma.admin.rule.*;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.internal.persistence.*;

/** Serializes configuration transitions per tenant; immutable version rows are never updated. */
@Service
@Transactional(readOnly = true)
public class RuleService implements RuleExecutionApi {
    private final AssetMapper assets;
    private final VersionMapper versions;
    private final ReleaseMapper releases;
    private final ExecutionMapper executions;
    private final WriteLockMapper locks;
    private final RuleJson json;
    private final RuleEvaluator evaluator;
    private final ExpressionEngine expressions;
    private final RuleTestService tests;
    private final List<RuleBindingPolicy> bindingPolicies;
    private final String environment;

    public RuleService(AssetMapper assets, VersionMapper versions, ReleaseMapper releases, ExecutionMapper executions,
            WriteLockMapper locks, RuleJson json, RuleEvaluator evaluator, ExpressionEngine expressions, RuleTestService tests,
            List<RuleBindingPolicy> bindingPolicies, @Value("${sma.rule.environment:default}") String environment) {
        this.assets = assets; this.versions = versions; this.releases = releases; this.executions = executions;
        this.locks = locks; this.json = json; this.evaluator = evaluator; this.expressions = expressions;
        this.tests = tests;
        this.bindingPolicies = List.copyOf(bindingPolicies); this.environment = environment;
    }
    public <T> T decode(String body, Class<T> type) { return checked(() -> json.read(body, type)); }
    public List<String> functions() { return expressions.functions(); }

    @Transactional public Asset createScene(Scene command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); identity(command.code(), command.name());
            if (command.inputSchema() == null || command.outputSchema() == null || command.parameterPolicy() == null
                    || command.allowedKinds() == null || command.allowedKinds().isEmpty()) invalid("scene", "CONTRACT_REQUIRED");
            RuleValues.validateSchema(command.inputSchema().fields(), 0);
            RuleValues.validateSchema(command.parameterPolicy().fields(), 0);
            RuleValues.validateSchema(Map.of("output", command.outputSchema()), 0);
            if (command.inputSchema().additionalProperties() || command.parameterPolicy().additionalProperties()) invalid("scene", "CLOSED_SCHEMA_REQUIRED");
            return view(insert("SCENE", command.code(), command.name(), null, json.write(command)));
        });
    }
    @Transactional public Asset createTemplate(Template command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); identity(command.code(), command.name());
            validateTemplate(command);
            return view(insert("TEMPLATE", command.code(), command.name(), command.sceneId(), json.write(command.document())));
        });
    }
    @Transactional public Asset saveScene(long id, SaveScene command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var row = require(id, "SCENE", true); revision(row, command.expectedRevision());
            var previous = json.read(row.getDraftJson(), Scene.class); var next = command.scene();
            if (next == null) invalid("scene", "REQUIRED");
            identity(next.code(), next.name());
            if (!previous.code().equals(next.code()) || !previous.inputSchema().equals(next.inputSchema())
                    || !previous.outputSchema().equals(next.outputSchema()) || !previous.parameterPolicy().equals(next.parameterPolicy())
                    || next.allowedKinds() == null || !next.allowedKinds().containsAll(previous.allowedKinds()))
                invalid("scene", "CONTRACT_IMMUTABLE_CREATE_NEW_SCENE");
            row.setName(next.name()); row.setDraftJson(json.write(next)); row.setRevision(row.getRevision() + 1);
            assets.updateById(row); return view(row);
        });
    }
    @Transactional public Asset saveTemplate(long id, Save command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var row = require(id, "TEMPLATE", true); revision(row, command.expectedRevision());
            var input = command.template(); if (input == null) invalid("template", "REQUIRED"); identity(input.code(), input.name());
            if (!input.code().equals(row.getCode()) || input.sceneId() != row.getReferenceId()) invalid("template", "IDENTITY_IMMUTABLE");
            validateTemplate(input); row.setName(input.name()); row.setDraftJson(json.write(input.document()));
            row.setRevision(row.getRevision() + 1); assets.updateById(row); return view(row);
        });
    }
    public Asset get(long id, String kind) { return view(require(id, kind, true)); }
    /** Pure lookup of the caller's original publication; current asset scope still applies. */
    public PublicationLookup publication(long id, String operationKey) {
        if (operationKey == null || !operationKey.matches("[a-zA-Z0-9_-]{8,64}")) throw new BizException(RuleErrorCode.NOT_FOUND);
        require(id, "TEMPLATE", true);
        var release = releases.selectOne(new LambdaQueryWrapper<ReleaseEntity>()
                .eq(ReleaseEntity::getPortal, CurrentUser.portal()).eq(ReleaseEntity::getAssetId, id)
                .eq(ReleaseEntity::getOperationKey, operationKey).eq(ReleaseEntity::getAction, "PUBLISH")
                .eq(ReleaseEntity::getCreatedBy, CurrentUser.userId()));
        if (release == null) throw new BizException(RuleErrorCode.NOT_FOUND);
        return new PublicationLookup(operationKey, releaseView(release));
    }
    public Asset templateScene(long templateId) {
        var template = require(templateId, "TEMPLATE", true);
        return view(require(template.getReferenceId(), "SCENE", false));
    }
    public List<Asset> catalogScenes(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) invalid("page", "INVALID_PAGE");
        return assets.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new LambdaQueryWrapper<AssetEntity>().eq(AssetEntity::getPortal, CurrentUser.portal())
                        .eq(AssetEntity::getKind, "SCENE").orderByAsc(AssetEntity::getId)).getRecords().stream().map(this::view).toList();
    }
    public Version instanceTemplate(long instanceId) {
        var instance = require(instanceId, "INSTANCE", true);
        var content = json.read(instance.getDraftJson(), Instance.class);
        return versionView(version(content.templateVersionId(), "TEMPLATE"));
    }
    public List<Asset> list(String kind, int page, int size) {
        if (!List.of("SCENE", "TEMPLATE", "INSTANCE").contains(kind) || page < 1 || size < 1 || size > 100) invalid("page", "INVALID_PAGE");
        var query = new LambdaQueryWrapper<AssetEntity>().eq(AssetEntity::getPortal, CurrentUser.portal()).eq(AssetEntity::getKind, kind);
        scope(query);
        return assets.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                query.orderByDesc(AssetEntity::getCreatedAt).orderByDesc(AssetEntity::getId)).getRecords().stream().map(this::view).toList();
    }
    public List<Version> versions(long assetId) {
        require(assetId, null, true);
        return versions.selectList(new LambdaQueryWrapper<VersionEntity>().eq(VersionEntity::getAssetId, assetId)
                .eq(VersionEntity::getPortal, CurrentUser.portal()).orderByDesc(VersionEntity::getRevision).last("LIMIT 100"))
                .stream().map(this::versionView).toList();
    }
    public List<Version> catalog(long sceneId) {
        return catalogEntries(sceneId).stream().map(CatalogEntry::version).toList();
    }
    public List<CatalogEntry> catalogEntries(long sceneId) {
        require(sceneId, "SCENE", false);
        return assets.selectList(new LambdaQueryWrapper<AssetEntity>().eq(AssetEntity::getKind, "TEMPLATE")
                .eq(AssetEntity::getPortal, CurrentUser.portal()).eq(AssetEntity::getReferenceId, sceneId)
                .eq(AssetEntity::getState, "PUBLISHED").isNotNull(AssetEntity::getActiveVersionId)
                .orderByAsc(AssetEntity::getCode).last("LIMIT 100")).stream()
                .map(asset -> new CatalogEntry(asset.getCode(), asset.getName(), versionView(version(asset.getActiveVersionId(), "TEMPLATE")))).toList();
    }
    @Transactional public Asset copyTemplate(long id, Copy command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var row = require(id, "TEMPLATE", true);
            String content = row.getDraftJson();
            if (command.versionId() != null) {
                var selected = version(command.versionId(), "TEMPLATE");
                if (!selected.getAssetId().equals(row.getId())) throw new BizException(RuleErrorCode.NOT_FOUND);
                content = selected.getContentJson();
            }
            return createTemplate(new Template(command.code(), command.name(), row.getReferenceId(), json.read(content, RuleTemplateDocument.class)));
        });
    }
    public VersionDiff diff(long assetId, long fromId, long toId) {
        var asset = require(assetId, null, true); var from = version(fromId, asset.getKind()); var to = version(toId, asset.getKind());
        if (!from.getAssetId().equals(asset.getId()) || !to.getAssetId().equals(asset.getId())) throw new BizException(RuleErrorCode.NOT_FOUND);
        var changes = new ArrayList<Change>();
        difference(json.read(from.getContentJson(), Object.class), json.read(to.getContentJson(), Object.class), "document", changes, 0);
        return new VersionDiff(assetId, fromId, toId, List.copyOf(changes), changes.size() >= 128);
    }
    public List<Asset> impacted(long templateId, int page, int size) {
        require(templateId, "TEMPLATE", true);
        if (page < 1 || page > 10000 || size < 1 || size > 100) invalid("page", "INVALID_PAGE");
        Long owner = CurrentUser.optional().filter(user -> !user.superAdmin() && !user.dataAccess().all())
                .map(user -> user.dataAccess().self() ? user.userId() : -1L).orElse(null);
        return assets.impacted(templateId, CurrentUser.portal(), owner, (page - 1) * size, size).stream().map(this::view).toList();
    }
    public Preview preview(long instanceId, Migration command) {
        var row = require(instanceId, "INSTANCE", true); revision(row, command.expectedRevision());
        var before = json.read(row.getDraftJson(), Instance.class);
        var previousVersion = version(before.templateVersionId(), "TEMPLATE");
        var target = version(command.templateVersionId(), "TEMPLATE");
        var previousTemplate = require(previousVersion.getAssetId(), "TEMPLATE", false);
        var targetTemplate = require(target.getAssetId(), "TEMPLATE", false);
        if (!previousTemplate.getReferenceId().equals(targetTemplate.getReferenceId())) throw new BizException(RuleErrorCode.INVALID, "SCENE_MISMATCH");
        var oldDoc = json.read(previousVersion.getContentJson(), RuleTemplateDocument.class);
        var newDoc = json.read(target.getContentJson(), RuleTemplateDocument.class);
        var changes = new ArrayList<Change>(); difference(json.read(json.write(oldDoc), Object.class), json.read(json.write(newDoc), Object.class), "template", changes, 0);
        try {
            var params = evaluator.parameters(newDoc, command.params() == null ? before.params() : command.params());
            var proposed = new Instance(before.code(), before.name(), target.getId(), params, before.objectType(), before.objectId(), before.environment(), before.startAt(), before.endAt());
            validateInstance(proposed, true);
            if (command.sampleInputs() == null || command.sampleInputs().isEmpty() || command.sampleInputs().size() > 20)
                throw new RuleFailure("sampleInputs", "SAMPLES_REQUIRED");
            var comparisons = new ArrayList<Comparison>(); Instant now = Instant.now();
            for (int i = 0; i < command.sampleInputs().size(); i++) {
                var input = command.sampleInputs().get(i);
                var oldResult = calculate(instanceId, row.getRevision(), previousVersion, before, input, now);
                var newResult = calculate(instanceId, row.getRevision(), target, proposed, input, now);
                comparisons.add(new Comparison(i, oldResult, newResult,
                        oldResult.status() != newResult.status() || !RuleEvaluator.deepEqual(oldResult.output(), newResult.output())));
            }
            boolean valid = comparisons.stream().allMatch(comparison -> normal(comparison.before().status()) && normal(comparison.after().status()));
            return new Preview(instanceId, row.getRevision(), valid, proposed, List.copyOf(changes), List.copyOf(comparisons), List.of());
        } catch (RuleFailure failure) {
            return new Preview(instanceId, row.getRevision(), false, null, List.copyOf(changes), List.of(), List.of(failure.problem()));
        }
    }
    @Transactional public Asset migrateDraft(long instanceId, Migration command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var preview = preview(instanceId, command);
            if (!preview.canMigrate()) invalid("migration", "PREVIEW_FAILED");
            return saveInstance(instanceId, new SaveInstance(command.expectedRevision(), preview.proposed()));
        });
    }
    private static boolean normal(Status status) { return status == Status.SUCCESS || status == Status.NOT_MATCHED; }
    private static void difference(Object before, Object after, String path, List<Change> changes, int depth) {
        if (changes.size() >= 128 || RuleEvaluator.deepEqual(before, after)) return;
        if (depth > 12) { changes.add(new Change(path, "STRUCTURE_CHANGED")); return; }
        if (before instanceof Map<?, ?> left && after instanceof Map<?, ?> right) {
            var keys = new java.util.TreeSet<String>(); left.keySet().forEach(key -> keys.add(key.toString())); right.keySet().forEach(key -> keys.add(key.toString()));
            for (String key : keys) {
                if (changes.size() >= 128) return;
                String next = path + "." + key;
                if (!left.containsKey(key)) changes.add(new Change(next, "ADDED"));
                else if (!right.containsKey(key)) changes.add(new Change(next, "REMOVED"));
                else difference(left.get(key), right.get(key), next, changes, depth + 1);
            }
        } else if (before instanceof List<?> left && after instanceof List<?> right) {
            int size = Math.max(left.size(), right.size());
            for (int i = 0; i < size && changes.size() < 128; i++) {
                if (i >= left.size()) changes.add(new Change(path + "[" + i + "]", "ADDED"));
                else if (i >= right.size()) changes.add(new Change(path + "[" + i + "]", "REMOVED"));
                else difference(left.get(i), right.get(i), path + "[" + i + "]", changes, depth + 1);
            }
        } else changes.add(new Change(path, "CHANGED"));
    }
    @Transactional public Release publishTemplate(long id, Publish command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var row = require(id, "TEMPLATE", true);
            var previous = replay(row, "PUBLISH", command.operationKey(), command.expectedRevision(), null);
            if (previous != null) return releaseView(previous);
            revision(row, command.expectedRevision());
            if (row.getState().equals("REVOKED") || row.getState().equals("DEPRECATED")) throw new BizException(RuleErrorCode.UNAVAILABLE);
            var document = json.read(row.getDraftJson(), RuleTemplateDocument.class);
            validateTemplate(new Template(row.getCode(), row.getName(), row.getReferenceId(), document));
            var report = tests.verifyCandidate(row.getId(), document, json.hash(row.getDraftJson()));
            if (!report.passed()) invalid("testRun." + report.id(), "TEST_SUITE_FAILED");
            var version = immutable(row, row.getReferenceId(), report);
            row.setActiveVersionId(version.getId()); row.setState("PUBLISHED");
            return release(row, version.getId(), "PUBLISH", command.operationKey(), command.expectedRevision(), null);
        });
    }
    @Transactional public Asset createInstance(Instance command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); identity(command.code(), command.name());
            var templateVersion = validateInstance(command, true);
            return view(insert("INSTANCE", command.code(), command.name(), templateVersion.getAssetId(), json.write(command)));
        });
    }
    @Transactional public Asset saveInstance(long id, SaveInstance command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var row = require(id, "INSTANCE", true); revision(row, command.expectedRevision());
            if (command.instance() == null) invalid("instance", "REQUIRED");
            if (!row.getCode().equals(command.instance().code())) invalid("instance", "IDENTITY_IMMUTABLE");
            identity(command.instance().code(), command.instance().name());
            var templateVersion = validateInstance(command.instance(), true);
            var oldTemplate = require(row.getReferenceId(), "TEMPLATE", false);
            var newTemplate = require(templateVersion.getAssetId(), "TEMPLATE", false);
            if (!oldTemplate.getReferenceId().equals(newTemplate.getReferenceId())) invalid("instance", "SCENE_IMMUTABLE");
            row.setName(command.instance().name()); row.setReferenceId(templateVersion.getAssetId());
            row.setDraftJson(json.write(command.instance())); row.setRevision(row.getRevision() + 1);
            assets.updateById(row); return view(row);
        });
    }
    @Transactional public RuleExecutionApi.Result trial(long id, Trial command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var row = require(id, "INSTANCE", true); revision(row, command.expectedRevision());
            var instance = json.read(row.getDraftJson(), Instance.class); var version = validateInstance(instance, true);
            var result = calculate(id, row.getRevision(), version, instance, command.input(), Instant.now());
            if (result.status() == Status.SUCCESS || result.status() == Status.NOT_MATCHED) {
                row.setTrialHash(json.hash(row.getDraftJson())); assets.updateById(row);
            }
            return result;
        });
    }
    @Transactional public Release publishInstance(long id, Publish command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var row = require(id, "INSTANCE", true);
            var previous = replay(row, "PUBLISH", command.operationKey(), command.expectedRevision(), null);
            if (previous != null) return releaseView(previous);
            revision(row, command.expectedRevision());
            var instance = json.read(row.getDraftJson(), Instance.class); validateInstance(instance, true);
            if (!json.hash(row.getDraftJson()).equals(row.getTrialHash())) invalid("instance", "TRIAL_REQUIRED");
            var version = immutable(row, instance.templateVersionId());
            row.setActiveVersionId(version.getId()); row.setState("PUBLISHED");
            return release(row, version.getId(), "PUBLISH", command.operationKey(), command.expectedRevision(), null);
        });
    }
    @Transactional public Release disable(long id, Publish command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var row = require(id, "INSTANCE", true);
            var previous = replay(row, "DISABLE", command.operationKey(), command.expectedRevision(), null);
            if (previous != null) return releaseView(previous);
            revision(row, command.expectedRevision()); row.setState("DISABLED");
            return release(row, row.getActiveVersionId(), "DISABLE", command.operationKey(), command.expectedRevision(), null);
        });
    }
    @Transactional public Release lifecycle(long id, Publish command, String action) {
        return checked(() -> {
            if (!List.of("DEPRECATE", "REVOKE").contains(action)) invalid("lifecycle", "INVALID_ACTION");
            locks.lock(CurrentUser.tenantId()); var row = require(id, "TEMPLATE", true);
            var previous = replay(row, action, command.operationKey(), command.expectedRevision(), null);
            if (previous != null) return releaseView(previous);
            revision(row, command.expectedRevision());
            if (row.getState().equals("REVOKED") || row.getActiveVersionId() == null) throw new BizException(RuleErrorCode.UNAVAILABLE);
            row.setState(action.equals("REVOKE") ? "REVOKED" : "DEPRECATED");
            return release(row, row.getActiveVersionId(), action, command.operationKey(), command.expectedRevision(), null);
        });
    }
    @Transactional public Release rollback(long id, Rollback command) {
        return checked(() -> {
            locks.lock(CurrentUser.tenantId()); var row = require(id, null, true);
            if (!List.of("TEMPLATE", "INSTANCE").contains(row.getKind())) invalid("rollback", "INVALID_KIND");
            var previous = replay(row, "ROLLBACK", command.operationKey(), command.expectedRevision(), command.versionId());
            if (previous != null) return releaseView(previous);
            revision(row, command.expectedRevision()); var target = version(command.versionId(), row.getKind());
            if (!target.getAssetId().equals(row.getId()) || row.getState().equals("REVOKED")) throw new BizException(RuleErrorCode.UNAVAILABLE);
            if (row.getKind().equals("INSTANCE")) validateInstance(json.read(target.getContentJson(), Instance.class), false);
            else { var document = json.read(target.getContentJson(), RuleTemplateDocument.class); evaluator.validate(document); evaluator.examples(document); }
            row.setActiveVersionId(target.getId()); row.setState("PUBLISHED");
            return release(row, target.getId(), "ROLLBACK", command.operationKey(), command.expectedRevision(), command.versionId());
        });
    }
    public Validation validate(Simulate command) {
        try { evaluator.validate(command.document()); return new Validation(true, List.of()); }
        catch (RuleFailure ex) { return new Validation(false, List.of(ex.problem())); }
    }
    public RuleExecutionApi.Result simulate(Simulate command) {
        return calculate(0, null, null, null, command.input(), Instant.now(), command.document(), command.params());
    }
    @Override @Transactional public RuleExecutionApi.Result execute(Request request) {
        if (request == null) throw new BizException(RuleErrorCode.INVALID, "request");
        var row = require(request.instanceId(), "INSTANCE", false); Instant now = Instant.now();
        long started = System.nanoTime();
        Result result;
        Long instanceRevision = null; Long templateVersionId = null;
        // Binding follows the immutable active version even while someone edits a different draft.
        var binding = json.read(row.getDraftJson(), Instance.class);
        if (row.getActiveVersionId() != null) {
            var configured = versions.selectById(row.getActiveVersionId());
            if (configured != null && configured.getAssetId().equals(row.getId()) && "INSTANCE".equals(configured.getKind())
                    && CurrentUser.portal().equals(configured.getPortal()) && json.hash(configured.getContentJson()).equals(configured.getContentHash())) {
                binding = json.read(configured.getContentJson(), Instance.class);
                instanceRevision = configured.getRevision(); templateVersionId = binding.templateVersionId();
            }
        }
        var bindingVersion = versions.selectById(binding.templateVersionId());
        if (bindingVersion == null || !"TEMPLATE".equals(bindingVersion.getKind()) || !CurrentUser.portal().equals(bindingVersion.getPortal()))
            throw new BizException(RuleErrorCode.NOT_FOUND);
        authorizeBinding(binding, bindingVersion);
        try {
            if (!row.getState().equals("PUBLISHED") || row.getActiveVersionId() == null)
                result = empty(row.getState().equals("DISABLED") ? Status.DISABLED : Status.NOT_CONFIGURED, now);
            else {
                var active = version(row.getActiveVersionId(), "INSTANCE"); instanceRevision = active.getRevision();
                var instance = json.read(active.getContentJson(), Instance.class); templateVersionId = instance.templateVersionId();
                var templateVersion = version(templateVersionId, "TEMPLATE"); authorizeBinding(instance, templateVersion);
                var template = require(templateVersion.getAssetId(), "TEMPLATE", false);
                if (!environment.equals(instance.environment())) result = empty(Status.NOT_CONFIGURED, now);
                else if (instance.startAt() != null && now.isBefore(instance.startAt()) || instance.endAt() != null && !now.isBefore(instance.endAt()))
                    result = empty(Status.NOT_EFFECTIVE, now);
                else if (template.getState().equals("REVOKED")) result = executionFailure(now, "TEMPLATE_REVOKED");
                else result = calculate(row.getId(), active.getRevision(), templateVersion, instance, request.input(), now);
            }
        } catch (RuleFailure | BizException ex) { result = executionFailure(now, "VERSION_UNAVAILABLE_OR_CORRUPT"); }
        var log = new ExecutionEntity(); log.setPortal(CurrentUser.portal()); log.setInstanceId(row.getId());
        log.setInstanceRevision(instanceRevision); log.setTemplateVersionId(templateVersionId); log.setReleaseRevision(row.getReleaseRevision());
        log.setStatus(result.status().name()); log.setContentHash(result.contentHash());
        try { log.setInputHash(json.hash(json.write(RuleValues.freeze(request.input(), 0)))); }
        catch (RuleFailure ignored) { /* Invalid Java input is never serialized through arbitrary bean getters. */ }
        log.setTraceJson(json.writeReport(result.trace()));
        log.setProblemsJson(json.write(result.problems())); log.setDurationMicros((System.nanoTime() - started) / 1000);
        executions.insert(log);
        return new Result(log.getId().toString(), result.status(), result.output(), instanceRevision,
                templateVersionId, result.contentHash(), now, result.trace(), result.problems(), row.getReleaseRevision());
    }
    private Result executionFailure(Instant now, String code) {
        return new Result(UUID.randomUUID().toString(), Status.ERROR, null, null, null, null, now,
                List.of(), List.of(new RuleProblem("version", code, null, null)));
    }
    public List<Map<String, Object>> executionList(long instanceId, int limit) {
        require(instanceId, "INSTANCE", true);
        if (limit < 1 || limit > 100) invalid("limit", "INVALID_PAGE");
        return executions.selectList(new LambdaQueryWrapper<ExecutionEntity>().eq(ExecutionEntity::getInstanceId, instanceId)
                .eq(ExecutionEntity::getPortal, CurrentUser.portal()).orderByDesc(ExecutionEntity::getCreatedAt).orderByDesc(ExecutionEntity::getId).last("LIMIT " + limit))
                .stream().map(row -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("id", row.getId().toString()); item.put("status", row.getStatus());
                    item.put("instanceRevision", row.getInstanceRevision()); item.put("releaseRevision", row.getReleaseRevision());
                    item.put("templateVersionId", row.getTemplateVersionId() == null ? null : row.getTemplateVersionId().toString());
                    item.put("durationMicros", row.getDurationMicros()); item.put("createdAt", row.getCreatedAt()); return item;
                }).toList();
    }
    private Result calculate(long id, Long revision, VersionEntity version, Instance instance, Map<String, Object> input, Instant now) {
        return calculate(id, revision, version, instance, input, now,
                json.read(version.getContentJson(), RuleTemplateDocument.class), instance.params());
    }
    private Result calculate(long id, Long revision, VersionEntity version, Instance instance, Map<String, Object> input,
            Instant now, RuleTemplateDocument document, Map<String, Object> params) {
        try {
            var evaluated = evaluator.evaluate(document, input, params, now);
            return new Result(UUID.randomUUID().toString(), evaluated.matched() ? Status.SUCCESS : Status.NOT_MATCHED,
                    evaluated.output(), revision, version == null ? null : version.getId(),
                    version == null ? json.hash(json.write(document)) : version.getContentHash(), now, evaluated.trace(), List.of());
        } catch (RuleFailure ex) {
            Status status = ex.problem().code().contains("TIME_OUT") ? Status.TIMEOUT
                    : ex.problem().path().startsWith("input") ? Status.INVALID_INPUT : Status.ERROR;
            return new Result(UUID.randomUUID().toString(), status, null, revision, version == null ? null : version.getId(),
                    version == null ? null : version.getContentHash(), now, List.of(), List.of(ex.problem()));
        }
    }
    private void validateTemplate(Template command) {
        var sceneRow = require(command.sceneId(), "SCENE", false);
        var scene = json.read(sceneRow.getDraftJson(), Scene.class); var doc = command.document(); evaluator.validate(doc);
        if (!scene.inputSchema().equals(doc.inputSchema()) || !scene.outputSchema().equals(doc.outputSchema())
                || !scene.allowedKinds().contains(doc.definition().kind())) invalid("template", "SCENE_CONTRACT_MISMATCH");
        doc.parameterSchema().fields().forEach((key, field) -> {
            var policy = scene.parameterPolicy().fields().get(key);
            policy(policy, field, "params." + key);
        });
    }
    private void policy(RuleSchema.Field policy, RuleSchema.Field field, String path) {
        if (policy == null || policy.type() != field.type()
                || policy.minimum() != null && (field.minimum() == null || field.minimum().compareTo(policy.minimum()) < 0)
                || policy.maximum() != null && (field.maximum() == null || field.maximum().compareTo(policy.maximum()) > 0)
                || policy.scale() != null && (field.scale() == null || field.scale() > policy.scale())
                || policy.maxLength() != null && (field.maxLength() == null || field.maxLength() > policy.maxLength())
                || policy.maxItems() != null && (field.maxItems() == null || field.maxItems() > policy.maxItems())
                || policy.enumValues() != null && !policy.enumValues().isEmpty() && (field.enumValues() == null
                    || field.enumValues().stream().anyMatch(value -> policy.enumValues().stream().noneMatch(allowed -> RuleValues.equal(value, allowed)))))
            invalid(path, "SCENE_POLICY_VIOLATION");
        if (field.type() == RuleSchema.Type.OBJECT)
            field.properties().forEach((key, child) -> policy(policy.properties().get(key), child, path + "." + key));
        if (field.type() == RuleSchema.Type.LIST) policy(policy.items(), field.items(), path + "[]");
    }
    private VersionEntity validateInstance(Instance input, boolean requirePublished) {
        if (input == null || input.params() == null || input.environment() == null || !input.environment().equals(environment)
                || input.startAt() != null && input.endAt() != null && !input.startAt().isBefore(input.endAt()))
            invalid("instance", "INVALID_INSTANCE");
        var version = version(input.templateVersionId(), "TEMPLATE"); var template = require(version.getAssetId(), "TEMPLATE", false);
        if (template.getState().equals("REVOKED") || requirePublished && !template.getState().equals("PUBLISHED"))
            throw new BizException(RuleErrorCode.UNAVAILABLE);
        var document = json.read(version.getContentJson(), RuleTemplateDocument.class);
        evaluator.parameters(document, input.params());
        authorizeBinding(input, version); return version;
    }
    private void authorizeBinding(Instance input, VersionEntity version) {
        if (input.objectType() == null || input.objectId() == null || input.objectType().length() > 64 || input.objectId().length() > 128)
            throw new AccessDeniedException("Binding authorization required");
        var template = require(version.getAssetId(), "TEMPLATE", false);
        var scene = require(template.getReferenceId(), "SCENE", false);
        if (bindingPolicies.stream().noneMatch(policy -> policy.supports(scene.getCode(), input.objectType()) && policy.canUse(input.objectId())))
            throw new AccessDeniedException("Binding authorization denied");
    }
    private AssetEntity insert(String kind, String code, String name, Long ref, String content) {
        var row = new AssetEntity(); row.setPortal(CurrentUser.portal()); row.setKind(kind); row.setCode(code); row.setName(name);
        row.setReferenceId(ref); row.setState("DRAFT"); row.setDraftJson(content); row.setRevision(1L); row.setReleaseRevision(0L);
        try { assets.insert(row); } catch (DuplicateKeyException ex) { throw new BizException(RuleErrorCode.CONFLICT); }
        return row;
    }
    private VersionEntity immutable(AssetEntity row, Long dependency) {
        return immutable(row, dependency, null);
    }
    private VersionEntity immutable(AssetEntity row, Long dependency, TestRun report) {
        var version = versions.selectOne(new LambdaQueryWrapper<VersionEntity>().eq(VersionEntity::getAssetId, row.getId())
                .eq(VersionEntity::getRevision, row.getRevision()));
        if (version != null) {
            if (!version.getContentHash().equals(json.hash(row.getDraftJson()))) throw new BizException(RuleErrorCode.CONFLICT);
            return version;
        }
        version = new VersionEntity(); version.setPortal(row.getPortal()); version.setAssetId(row.getId()); version.setKind(row.getKind());
        version.setRevision(row.getRevision()); version.setContentJson(row.getDraftJson());
        version.setContentHash(json.hash(row.getDraftJson())); version.setDependencyId(dependency);
        if (report != null) { version.setTestRunId(report.id()); version.setCaseSetHash(report.caseSetHash()); }
        versions.insert(version); return version;
    }
    private Release release(AssetEntity row, Long versionId, String action, String key, long expected, Long target) {
        row.setReleaseRevision(row.getReleaseRevision() + 1); row.setRevision(row.getRevision() + 1); assets.updateById(row);
        var release = new ReleaseEntity(); release.setPortal(CurrentUser.portal()); release.setAssetId(row.getId()); release.setVersionId(versionId);
        release.setReleaseRevision(row.getReleaseRevision()); release.setAction(action); release.setOperationKey(key);
        release.setFingerprint(fingerprint(row.getId(), action, expected, target)); releases.insert(release); return releaseView(release);
    }
    private ReleaseEntity replay(AssetEntity row, String action, String key, long expected, Long target) {
        if (key == null || !key.matches("[a-zA-Z0-9_-]{8,64}")) invalid("operationKey", "INVALID_OPERATION_KEY");
        var previous = releases.selectOne(new LambdaQueryWrapper<ReleaseEntity>().eq(ReleaseEntity::getPortal, CurrentUser.portal())
                .eq(ReleaseEntity::getOperationKey, key));
        if (previous != null && (!previous.getFingerprint().equals(fingerprint(row.getId(), action, expected, target))
                || !previous.getCreatedBy().equals(CurrentUser.userId()))) throw new BizException(RuleErrorCode.CONFLICT);
        return previous;
    }
    private String fingerprint(long id, String action, long revision, Long target) { return json.hash(id + ":" + action + ":" + revision + ":" + target); }
    private AssetEntity require(long id, String kind, boolean manage) {
        var row = assets.selectById(id);
        if (row == null || !CurrentUser.portal().equals(row.getPortal()) || kind != null && !kind.equals(row.getKind()))
            throw new BizException(RuleErrorCode.NOT_FOUND);
        if (manage) checkScope(row);
        return row;
    }
    private VersionEntity version(long id, String kind) {
        var row = versions.selectById(id);
        if (row == null || !CurrentUser.portal().equals(row.getPortal()) || !kind.equals(row.getKind())) throw new BizException(RuleErrorCode.NOT_FOUND);
        if (!json.hash(row.getContentJson()).equals(row.getContentHash())) throw new BizException(RuleErrorCode.UNAVAILABLE);
        return row;
    }
    private void checkScope(AssetEntity row) {
        var user = CurrentUser.optional();
        if (user.isPresent() && !user.get().superAdmin() && !user.get().dataAccess().all()
                && !(user.get().dataAccess().self() && row.getCreatedBy().equals(user.get().userId())))
            throw new AccessDeniedException("Rule data scope denied");
    }
    private void scope(LambdaQueryWrapper<AssetEntity> query) {
        CurrentUser.optional().ifPresent(user -> {
            if (!user.superAdmin() && !user.dataAccess().all())
                query.eq(AssetEntity::getCreatedBy, user.dataAccess().self() ? user.userId() : -1L);
        });
    }
    private void revision(AssetEntity row, long expected) { if (row.getRevision() != expected) throw new BizException(RuleErrorCode.CONFLICT); }
    private void identity(String code, String name) {
        if (code == null || !code.matches("[a-z][a-z0-9_-]{0,63}") || name == null || name.isBlank() || name.length() > 128)
            invalid("identity", "INVALID_IDENTITY");
    }
    private Asset view(AssetEntity row) {
        Object draft = switch (row.getKind()) { case "SCENE" -> json.read(row.getDraftJson(), Scene.class);
            case "TEMPLATE" -> json.read(row.getDraftJson(), RuleTemplateDocument.class); default -> json.read(row.getDraftJson(), Instance.class); };
        return new Asset(row.getId(), row.getKind(), row.getCode(), row.getName(), row.getRevision(), row.getReleaseRevision(),
                row.getState(), row.getActiveVersionId(), draft);
    }
    private Version versionView(VersionEntity row) {
        Object document = row.getKind().equals("TEMPLATE") ? json.read(row.getContentJson(), RuleTemplateDocument.class) : json.read(row.getContentJson(), Instance.class);
        return new Version(row.getId(), row.getAssetId(), row.getRevision(), row.getContentHash(), row.getDependencyId(), document,
                row.getTestRunId(), row.getCaseSetHash());
    }
    private Release releaseView(ReleaseEntity row) { return new Release(row.getId(), row.getAssetId(), row.getVersionId(), row.getReleaseRevision(), row.getAction()); }
    private Result empty(Status status, Instant now) { return new Result(UUID.randomUUID().toString(), status, null, null, null, null, now, List.of(), List.of()); }
    private static void invalid(String path, String code) { throw new RuleFailure(path, code); }
    private <T> T checked(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (RuleFailure ex) { throw new BizException(RuleErrorCode.INVALID, ex.problem().path() + ":" + ex.problem().code()); }
    }
}
