package com.sma.admin.rule;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.internal.service.RuleJson;
import com.sma.admin.rule.internal.service.RuleService;
import com.sma.admin.support.*;

@org.springframework.boot.test.context.SpringBootTest(webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc @Import({RulePlatformTests.Bindings.class, TenantMockMvcConfiguration.class})
@org.springframework.test.context.TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class RulePlatformTests {
    private static final String NETWORK_HOST="127.0.0.51";
    @TestConfiguration(proxyBeanMethods = false)
    static class Bindings {
        @Bean RuleBindingPolicy ruleTestBindings() { return new RuleBindingPolicy() {
            @Override public boolean supports(String scene, String type) { return type.equals("TEST_OBJECT"); }
            @Override public boolean canUse(String id) { return id.startsWith("owned-") && (!id.equals("owned-private") || CurrentUser.userId() != 1024L); }
        }; }
    }
    @Autowired RuleService service;
    @Autowired RuleJson json;
    @Autowired com.sma.admin.rule.internal.service.RuleTestService tests;
    @Autowired com.sma.admin.rule.internal.service.RuleBatchService batches;
    @Autowired com.sma.admin.rule.internal.service.RuleOperationsService operations;
    @Autowired com.sma.admin.rule.internal.service.RuleImpactService impacts;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.sma.admin.rule.internal.persistence.MigrationBatchMapper batchRows;
    @Autowired MockMvcTester mvc;
    @Autowired org.springframework.test.web.servlet.MockMvc rawMvc;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired com.sma.admin.iam.internal.service.UserService users;
    @Autowired com.sma.admin.iam.internal.service.RoleService roles;
    @Autowired com.sma.admin.iam.internal.service.PermissionService permissions;
    @Autowired com.sma.admin.iam.internal.service.RolePermissionService grants;
    @Autowired com.sma.admin.auth.internal.service.SessionIssuer issuer;
    @Autowired com.sma.admin.audit.internal.persistence.OperationLogMapper auditLogs;
    @Autowired com.sma.admin.tenant.internal.persistence.DomainMapper domains;
    @Autowired com.sma.admin.rule.internal.service.RulePermissionCatalog catalog;

    private SmaPrincipal user(long tenant, long id, String... permissions) {
        return new SmaPrincipal(id, "rule-tester", tenant, "admin", 10L, false, DataAccess.unrestricted(), Set.of(permissions), "UTC");
    }
    private RuleSchema input() { return new RuleSchema(Map.of("amount", RuleSchema.Field.required(RuleSchema.Type.DECIMAL)), false); }
    private RuleSchema.Field output() {
        return new RuleSchema.Field(RuleSchema.Type.DECIMAL, true, BigDecimal.ZERO, new BigDecimal("1000000"), 2,
                null, null, null, false, null, null, null);
    }
    private RuleSchema params() {
        return new RuleSchema(Map.of("ratio", new RuleSchema.Field(RuleSchema.Type.DECIMAL, true,
                BigDecimal.ZERO, BigDecimal.ONE, 4, null, null, null, false, null, null, null)), false);
    }
    private RuleTemplateDocument document(String expression) {
        return new RuleTemplateDocument(1, input(), params(), output(), RuleDefinition.expression(expression),
                Map.of("ratio", new RuleTemplateDocument.ParameterUi("折后比例", "0.9 表示九折", "基本", 1)),
                List.of(new RuleTemplateDocument.Example("normal", Map.of("amount", new BigDecimal("100")),
                        Map.of("ratio", new BigDecimal("0.9")), new BigDecimal("90"))));
    }
    private long template(String code) {
        var scene = service.createScene(new Scene(code, "折扣场景", input(), output(), params(), List.of(RuleDefinition.Kind.EXPRESSION, RuleDefinition.Kind.CONDITION)));
        var template = service.createTemplate(new Template(code, "折扣模板", scene.id(), document("round(input.amount * params.ratio, 2)")));
        return service.publishTemplate(template.id(), new Publish(template.revision(), "publish_" + code)).versionId();
    }
    private Instance instance(String code, long version, String ratio) {
        return new Instance(code, "活动折扣", version, Map.of("ratio", new BigDecimal(ratio)), "TEST_OBJECT", "owned-" + code,
                "default", null, null);
    }
    private Asset live(String code, long version, String ratio) {
        var row = service.createInstance(instance(code, version, ratio));
        assertThat(service.trial(row.id(), new Trial(row.revision(), Map.of("amount", new BigDecimal("100")))).status())
                .isEqualTo(RuleExecutionApi.Status.SUCCESS);
        service.publishInstance(row.id(), new Publish(row.revision(), "instance_" + code));
        return service.get(row.id(), "INSTANCE");
    }
    @Test void publishedTemplatesSupportIndependentInstancesAndExactExecutionSnapshots() {
        Users.as(user(5101, 1001), () -> {
            long version = template("snapshot");
            var ownerTemplate = service.list("TEMPLATE", 1, 20).getFirst();
            assertThat(service.templateScene(ownerTemplate.id()).code()).isEqualTo("snapshot");
            Users.as(user(99999, 99999), () -> {
                assertThatThrownBy(() -> service.templateScene(ownerTemplate.id())).isInstanceOf(BizException.class); return null;
            });
            var first = live("first", version, "0.9"); var second = live("second", version, "0.5");
            assertThat(service.catalogScenes(1, 20)).anyMatch(scene -> scene.code().equals("snapshot"));
            assertThat(service.instanceTemplate(first.id()).id()).isEqualTo(version);
            var catalogScene = service.templateScene(ownerTemplate.id());
            assertThat(service.catalogEntries(catalogScene.id())).first().satisfies(entry -> {
                assertThat(entry.name()).isEqualTo(ownerTemplate.name()); assertThat(entry.version().id()).isEqualTo(version);
            });
            Users.as(user(99999, 99999), () -> {
                assertThat(service.catalogScenes(1, 20)).noneMatch(scene -> scene.code().equals("snapshot"));
                assertThatThrownBy(() -> service.instanceTemplate(first.id())).isInstanceOf(BizException.class); return null;
            });
            var a = service.execute(new RuleExecutionApi.Request(first.id(), Map.of("amount", new BigDecimal("300.15"))));
            var b = service.execute(new RuleExecutionApi.Request(second.id(), Map.of("amount", new BigDecimal("300.15"))));
            assertThat(a.output()).isEqualTo(new BigDecimal("270.14")); assertThat(b.output()).isEqualTo(new BigDecimal("150.08"));
            assertThat(a.templateVersionId()).isEqualTo(version); assertThat(a.trace()).isNotEmpty();
            assertThat(service.executionList(first.id(), 20)).hasSize(1);
            var inputError = service.execute(new RuleExecutionApi.Request(first.id(), Map.of()));
            assertThat(inputError.status()).isEqualTo(RuleExecutionApi.Status.INVALID_INPUT);
            return null;
        });
    }
    @Test void draftSaveDoesNotChangePublishedVersionAndRollbackUsesImmutableContent() {
        Users.as(user(5102, 1002), () -> {
            long version = template("rollback"); var row = live("rollback_live", version, "0.9");
            var old = row.activeVersionId();
            var changed = service.saveInstance(row.id(), new SaveInstance(row.revision(), instance(row.code(), version, "0.5")));
            assertThat(service.execute(new RuleExecutionApi.Request(row.id(), Map.of("amount", 100))).output()).isEqualTo(new BigDecimal("90.00"));
            assertThatThrownBy(() -> service.publishInstance(row.id(), new Publish(changed.revision(), "publish_notrial"))).isInstanceOf(BizException.class);
            service.trial(row.id(), new Trial(changed.revision(), Map.of("amount", 100)));
            service.publishInstance(row.id(), new Publish(changed.revision(), "publish_aftertrial"));
            assertThat(service.execute(new RuleExecutionApi.Request(row.id(), Map.of("amount", 100))).output()).isEqualTo(new BigDecimal("50.00"));
            var latest = service.get(row.id(), "INSTANCE");
            service.rollback(row.id(), new Rollback(latest.revision(), old, "rollback_operation"));
            assertThat(service.execute(new RuleExecutionApi.Request(row.id(), Map.of("amount", 100))).output()).isEqualTo(new BigDecimal("90.00"));
            assertThat(service.versions(row.id())).hasSize(2);
            return null;
        });
    }
    @Test void staleEditsDuplicateOperationsAndStopAreHandledExplicitly() {
        Users.as(user(5103, 1003), () -> {
            long version = template("idempotent"); var row = service.createInstance(instance("replay", version, "0.9"));
            service.trial(row.id(), new Trial(row.revision(), Map.of("amount", 100)));
            var command = new Publish(row.revision(), "same_operation_key");
            var first = service.publishInstance(row.id(), command); var repeated = service.publishInstance(row.id(), command);
            assertThat(repeated).isEqualTo(first);
            assertThatThrownBy(() -> service.saveInstance(row.id(), new SaveInstance(row.revision(), instance(row.code(), version, "0.7"))))
                    .isInstanceOf(BizException.class);
            var current = service.get(row.id(), "INSTANCE");
            service.disable(row.id(), new Publish(current.revision(), "disable_operation"));
            assertThat(service.execute(new RuleExecutionApi.Request(row.id(), Map.of("amount", 100))).status()).isEqualTo(RuleExecutionApi.Status.DISABLED);
            return null;
        });
    }
    @Test void tenancyPortalDataScopeAndBindingAuthorizationAreEnforced() {
        Users.as(user(5104, 1004), () -> {
            long version = template("isolation"); var row = live("owned_instance", version, "0.9");
            Users.as(user(5105, 1005), () -> {
                assertThat(service.list("INSTANCE", 1, 20)).isEmpty();
                assertThatThrownBy(() -> service.execute(new RuleExecutionApi.Request(row.id(), Map.of("amount", 100)))).isInstanceOf(BizException.class);
                assertThatThrownBy(() -> service.createInstance(instance("other_tenant", version, "0.9"))).isInstanceOf(BizException.class);
                return null;
            });
            var forged = new Instance("forged", "forged", version, Map.of("ratio", new BigDecimal("0.9")), "TEST_OBJECT", "foreign-id", "default", null, null);
            assertThatThrownBy(() -> service.createInstance(forged)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            var limited = new SmaPrincipal(9999, "limited", 5104, "admin", 10L, false, DataAccess.own(), Set.of(), "UTC");
            Users.as(limited, () -> { assertThat(service.list("INSTANCE", 1, 20)).isEmpty();
                assertThatThrownBy(() -> service.get(row.id(), "INSTANCE")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class); return null; });
            var portal = new SmaPrincipal(1004, "portal", 5104, "other", 10L, false, DataAccess.unrestricted(), Set.of(), "UTC");
            Users.as(portal, () -> { assertThatThrownBy(() -> service.get(row.id(), "INSTANCE")).isInstanceOf(BizException.class); return null; });
            return null;
        });
    }
    @Test void parameterBoundsAndHalfOpenEffectiveDatesAreApplied() {
        Users.as(user(5106, 1006), () -> {
            long version = template("ranges");
            assertThatThrownBy(() -> service.createInstance(instance("invalid_ratio", version, "1.1"))).isInstanceOf(BizException.class);
            var row = service.createInstance(new Instance("expired", "expired", version, Map.of("ratio", new BigDecimal("0.9")),
                    "TEST_OBJECT", "owned-expired", "default", Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-02-01T00:00:00Z")));
            service.trial(row.id(), new Trial(row.revision(), Map.of("amount", 100)));
            service.publishInstance(row.id(), new Publish(row.revision(), "publish_expired"));
            assertThat(service.execute(new RuleExecutionApi.Request(row.id(), Map.of("amount", 100))).status()).isEqualTo(RuleExecutionApi.Status.NOT_EFFECTIVE);
            return null;
        });
    }
    @Test void templatePolicyCannotBroadenScenePrecisionAndBadExamplesCannotPublish() {
        Users.as(user(5108, 1008), () -> {
            var scene = service.createScene(new Scene("policy", "Policy", input(), output(), params(), List.of(RuleDefinition.Kind.EXPRESSION)));
            var ordinary = document("round(input.amount * params.ratio, 2)");
            var broad = new RuleSchema(Map.of("ratio", new RuleSchema.Field(RuleSchema.Type.DECIMAL, true,
                    BigDecimal.ZERO, BigDecimal.ONE, 8, null, null, null, false, null, null, null)), false);
            var document = new RuleTemplateDocument(1, input(), broad, output(), ordinary.definition(), ordinary.parameterUi(), ordinary.examples());
            assertThatThrownBy(() -> service.createTemplate(new Template("too_broad", "Bad", scene.id(), document))).isInstanceOf(BizException.class);
            var broken = new RuleTemplateDocument(1, input(), params(), output(), ordinary.definition(), ordinary.parameterUi(),
                    List.of(new RuleTemplateDocument.Example("wrong expectation", Map.of("amount", 100),
                            Map.of("ratio", new BigDecimal("0.9")), 1)));
            var row = service.createTemplate(new Template("bad_example", "Bad example", scene.id(), broken));
            assertThatThrownBy(() -> service.publishTemplate(row.id(), new Publish(row.revision(), "bad_example_publish"))).isInstanceOf(BizException.class);
            assertThat(service.catalog(scene.id())).isEmpty();
            return null;
        });
    }
    @Test void templateUpgradeDoesNotRebindExistingInstancesAndUnknownSchemasAreRejected() {
        Users.as(user(5109, 1009), () -> {
            var scene = service.createScene(new Scene("upgrade", "Upgrade", input(), output(), params(), List.of(RuleDefinition.Kind.EXPRESSION)));
            var template = service.createTemplate(new Template("upgrade", "Upgrade", scene.id(), document("round(input.amount * params.ratio, 2)")));
            long firstVersion = service.publishTemplate(template.id(), new Publish(template.revision(), "upgrade_publish_first")).versionId();
            var oldInstance = live("old_binding", firstVersion, "0.9");
            var upgraded = new RuleTemplateDocument(1, input(), params(), output(),
                    RuleDefinition.expression("round(input.amount * params.ratio * 0.9, 2)"), Map.of(),
                    List.of(new RuleTemplateDocument.Example("new calculation", Map.of("amount", 100),
                            Map.of("ratio", new BigDecimal("0.9")), new BigDecimal("81"))));
            var current = service.get(template.id(), "TEMPLATE");
            var saved = service.saveTemplate(template.id(), new Save(current.revision(), new Template(template.code(), template.name(), scene.id(), upgraded)));
            long nextVersion = service.publishTemplate(template.id(), new Publish(saved.revision(), "upgrade_publish_second")).versionId();
            assertThat(service.catalog(scene.id())).extracting(Version::id).containsExactly(nextVersion);
            var original = service.execute(new RuleExecutionApi.Request(oldInstance.id(), Map.of("amount", 100)));
            assertThat(original.output()).isEqualTo(new BigDecimal("90.00")); assertThat(original.templateVersionId()).isEqualTo(firstVersion);
            assertThatThrownBy(() -> service.decode("{\"unknownSchema\":true}", Scene.class)).isInstanceOf(BizException.class);
            var before = service.get(oldInstance.id(), "INSTANCE");
            var migration = new Migration(before.revision(), nextVersion, null, List.of(Map.of("amount", 100)));
            var preview = service.preview(before.id(), migration);
            assertThat(preview.canMigrate()).isTrue(); assertThat(preview.comparisons()).singleElement().satisfies(comparison -> {
                assertThat(comparison.changed()).isTrue(); assertThat(comparison.before().output()).isEqualTo(new BigDecimal("90.00"));
                assertThat(comparison.after().output()).isEqualTo(new BigDecimal("81.00"));
            });
            assertThat(service.diff(template.id(), firstVersion, nextVersion).changes()).isNotEmpty();
            assertThat(service.impacted(template.id(), 1, 20)).extracting(Asset::id).contains(before.id());
            var draft = service.migrateDraft(before.id(), migration);
            assertThat(((Instance) draft.draft()).templateVersionId()).isEqualTo(nextVersion);
            assertThat(service.execute(new RuleExecutionApi.Request(before.id(), Map.of("amount", 100))).output()).isEqualTo(new BigDecimal("90.00"));
            assertThatThrownBy(() -> service.publishInstance(before.id(), new Publish(draft.revision(), "upgrade_without_trial"))).isInstanceOf(BizException.class);
            service.trial(before.id(), new Trial(draft.revision(), Map.of("amount", 100)));
            service.publishInstance(before.id(), new Publish(draft.revision(), "upgrade_instance_publish"));
            assertThat(service.execute(new RuleExecutionApi.Request(before.id(), Map.of("amount", 100))).output()).isEqualTo(new BigDecimal("81.00"));
            return null;
        });
    }
    @Test void concurrentEditorsCannotSilentlyOverwriteTheSameRevision() throws Exception {
        var actor = user(5110, 1010);
        Asset row = Users.as(actor, () -> live("concurrent", template("concurrent_scene"), "0.9"));
        long version = ((Instance) row.draft()).templateVersionId();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (String ratio : List.of("0.7", "0.8")) results.add(pool.submit(() -> {
                start.await();
                return Users.as(actor, () -> {
                    try { service.saveInstance(row.id(), new SaveInstance(row.revision(), instance(row.code(), version, ratio))); return true; }
                    catch (BizException ex) { assertThat(ex.getErrorCode()).isEqualTo(RuleErrorCode.CONFLICT); return false; }
                });
            }));
            start.countDown();
            int saved = 0; for (var result : results) if (result.get(20, java.util.concurrent.TimeUnit.SECONDS)) saved++;
            assertThat(saved).isEqualTo(1);
        }
        assertThat(Users.as(actor, () -> service.get(row.id(), "INSTANCE")).revision()).isEqualTo(row.revision() + 1);
    }
    @Test void flowTemplatePublishesAndExecutesAFixedVersionSubrule() {
        Users.as(user(5111, 1011), () -> {
            long child = template("flow_child");
            var nodes = List.of(
                    new RuleDefinition.Node("start", RuleDefinition.NodeKind.START, null, null, "eligible", null, null, null, null, null, null),
                    new RuleDefinition.Node("eligible", RuleDefinition.NodeKind.CONDITION, "input.amount >= 300", null, null, "price", "unchanged", null, null, null, null),
                    new RuleDefinition.Node("price", RuleDefinition.NodeKind.SUBRULE, null, null, "result", null, null, child,
                            Map.of("amount", "input.amount"), Map.of("ratio", "params.ratio"), output()),
                    new RuleDefinition.Node("result", RuleDefinition.NodeKind.RESULT, "context.steps.price", null, null, null, null, null, null, null, null),
                    new RuleDefinition.Node("unchanged", RuleDefinition.NodeKind.RESULT, "input.amount", null, null, null, null, null, null, null, null));
            var flow = new RuleDefinition(RuleDefinition.Kind.FLOW, null, null, null, null, new RuleDefinition.Flow("start", nodes));
            var document = new RuleTemplateDocument(1, input(), params(), output(), flow, Map.of(),
                    List.of(new RuleTemplateDocument.Example("under threshold", Map.of("amount", 100), Map.of("ratio", new BigDecimal(".9")), 100)));
            var scene = service.createScene(new Scene("flow_scene", "Flow scene", input(), output(), params(), List.of(RuleDefinition.Kind.FLOW)));
            var row = service.createTemplate(new Template("flow_template", "Flow template", scene.id(), document));
            var published = service.publishTemplate(row.id(), new Publish(row.revision(), "publish_flow_template"));
            var instance = live("flow_instance", published.versionId(), ".9");
            var result = service.execute(new RuleExecutionApi.Request(instance.id(), Map.of("amount", 400)));
            assertThat(result.status()).isEqualTo(RuleExecutionApi.Status.SUCCESS);
            assertThat(result.output()).isEqualTo(new BigDecimal("360.00"));
            assertThat(result.trace()).anyMatch(trace -> trace.id().startsWith("price/"))
                    .anyMatch(trace -> trace.id().equals("unchanged") && !trace.evaluated());
            return null;
        });
    }
    @Test void decisionTableCanBeCopiedAndComposedIntoAPublishedFlow() {
        Users.as(user(5112, 1012), () -> {
            var objectOutput = new RuleSchema.Field(RuleSchema.Type.OBJECT, true, null, null, null, null, null, null, false,
                    Map.of("ratio", output(), "reason", RuleSchema.Field.required(RuleSchema.Type.STRING)), null, null);
            var table = new RuleDefinition.DecisionTable(RuleDefinition.HitPolicy.UNIQUE,
                    List.of(new RuleDefinition.Column("amount", new RuleDefinition.Operand("input", "amount", null), output())),
                    List.of(new RuleDefinition.Row("high", Map.of("amount", new RuleDefinition.Cell("GE", new RuleDefinition.Operand("literal", null, 300))),
                            Map.of("ratio", "params.ratio", "reason", "'HIGH'"))), Map.of("ratio", BigDecimal.ONE, "reason", "DEFAULT"));
            var document = new RuleTemplateDocument(1, input(), params(), objectOutput,
                    new RuleDefinition(RuleDefinition.Kind.DECISION_TABLE, null, null, null, null, null, table), Map.of(),
                    List.of(new RuleTemplateDocument.Example("default", Map.of("amount", 100), Map.of("ratio", new BigDecimal(".9")), Map.of("ratio", BigDecimal.ONE, "reason", "DEFAULT"))));
            var scene = service.createScene(new Scene("table_scene", "Table scene", input(), objectOutput, params(), List.of(RuleDefinition.Kind.DECISION_TABLE)));
            var template = service.createTemplate(new Template("table_template", "Table", scene.id(), document));
            long version = service.publishTemplate(template.id(), new Publish(template.revision(), "table_publish_first")).versionId();
            var copied = service.copyTemplate(template.id(), new Copy("table_copy", "Table copy", version));
            assertThat(copied.state()).isEqualTo("DRAFT"); assertThat(copied.activeVersionId()).isNull();
            assertThat(service.catalog(scene.id())).hasSize(1);
            var parentScene = service.createScene(new Scene("table_flow", "Table flow", input(), output(), params(), List.of(RuleDefinition.Kind.FLOW)));
            var nodes = List.of(new RuleDefinition.Node("start", RuleDefinition.NodeKind.START, null, null, "table", null, null, null, null, null, null),
                    new RuleDefinition.Node("table", RuleDefinition.NodeKind.TABLE, null, null, "result", null, null, version,
                            Map.of("amount", "input.amount"), Map.of("ratio", "params.ratio"), objectOutput),
                    new RuleDefinition.Node("result", RuleDefinition.NodeKind.RESULT, "round(input.amount * context.steps.table.ratio, 2)", null,
                            null, null, null, null, null, null, null));
            var parentDoc = new RuleTemplateDocument(1, input(), params(), output(),
                    new RuleDefinition(RuleDefinition.Kind.FLOW, null, null, null, null, new RuleDefinition.Flow("start", nodes)), Map.of(),
                    List.of(new RuleTemplateDocument.Example("default", Map.of("amount", 100), Map.of("ratio", new BigDecimal(".9")), 100)));
            var parent = service.createTemplate(new Template("table_flow_template", "Table flow", parentScene.id(), parentDoc));
            var parentVersion = service.publishTemplate(parent.id(), new Publish(parent.revision(), "table_flow_publish"));
            var instance = live("table_flow_instance", parentVersion.versionId(), ".9");
            var result = service.execute(new RuleExecutionApi.Request(instance.id(), Map.of("amount", 400)));
            assertThat(result.output()).isEqualTo(new BigDecimal("360.00"));
            assertThat(result.trace()).anyMatch(item -> item.id().startsWith("table/high"));
            return null;
        });
    }
    @Test void testSuitesBindVersionAndCaseHashesAndFailedPublishReportsSurviveRollback() {
        Users.as(user(5120, 1020), () -> {
            long version = template("suite"); var template = service.list("TEMPLATE", 1, 20).getFirst();
            var positive = tests.save(template.id(), null, new CaseSave(0, new CaseInput("normal", Map.of("amount", 100),
                    Map.of("ratio", new BigDecimal(".9")), RuleExecutionApi.Status.SUCCESS, new BigDecimal("90"), null, true, null)));
            tests.save(template.id(), null, new CaseSave(0, new CaseInput("missing amount", Map.of(), Map.of("ratio", new BigDecimal(".9")),
                    RuleExecutionApi.Status.INVALID_INPUT, null, "REQUIRED", true, null)));
            var passed = tests.run(template.id(), new RunInput(version, null));
            assertThat(passed.passed()).isTrue(); assertThat(passed.total()).isEqualTo(3);
            assertThat(passed.contentHash()).isEqualTo(service.versions(template.id()).getFirst().contentHash());
            tests.save(template.id(), positive.id(), new CaseSave(positive.revision(), new CaseInput("wrong expectation", Map.of("amount", 100),
                    Map.of("ratio", new BigDecimal(".9")), RuleExecutionApi.Status.SUCCESS, new BigDecimal("91"), null, true, null)));
            var failed = tests.run(template.id(), new RunInput(version, null));
            assertThat(failed.passed()).isFalse(); assertThat(failed.caseSetHash()).isNotEqualTo(passed.caseSetHash());
            var current = service.get(template.id(), "TEMPLATE");
            assertThatThrownBy(() -> service.publishTemplate(template.id(), new Publish(current.revision(), "publish_bad_suite"))).isInstanceOf(BizException.class);
            assertThat(service.get(template.id(), "TEMPLATE").activeVersionId()).isEqualTo(version);
            assertThat(tests.runs(template.id(), 20)).first().satisfies(report -> { assertThat(report.passed()).isFalse(); assertThat(report.versionId()).isNull(); });
            var evidence = service.versions(template.id()).getFirst();
            assertThat(evidence.testRunId()).isNotNull(); assertThat(evidence.caseSetHash()).isNotBlank();
            return null;
        });
    }
    @Test void migrationBatchCommitsEachDraftAndReceiptOnceAndReportsPartialFailure() {
        Users.as(user(5121, 1021), () -> {
            long oldVersion = template("batch"); var template = service.list("TEMPLATE", 1, 20).getFirst();
            var one = live("batch_one", oldVersion, ".9"); var two = live("batch_two", oldVersion, ".9");
            var upgraded = new RuleTemplateDocument(1, input(), params(), output(), RuleDefinition.expression("round(input.amount * params.ratio * 0.9, 2)"), Map.of(),
                    List.of(new RuleTemplateDocument.Example("normal", Map.of("amount", 100), Map.of("ratio", new BigDecimal(".9")), 81)));
            var saved = service.saveTemplate(template.id(), new Save(template.revision(), new Template(template.code(), template.name(),
                    ((Number) service.versions(template.id()).getFirst().dependencyId()).longValue(), upgraded)));
            long nextVersion = service.publishTemplate(template.id(), new Publish(saved.revision(), "publish_batch_upgrade")).versionId();
            var command = new BatchMigration("batch_migration_key", List.of(
                    new MigrationItem(one.id(), new Migration(one.revision(), nextVersion, null, List.of(Map.of("amount", 100)))),
                    new MigrationItem(two.id(), new Migration(1, nextVersion, null, List.of(Map.of("amount", 100))))));
            var result = batches.migrate(command);
            assertThat(result.state()).isEqualTo("COMPLETED_WITH_ERRORS"); assertThat(result.results()).hasSize(2);
            Users.as(new SmaPrincipal(1021L, "no-scope", 5121L, "admin", 10L, false, DataAccess.none(), Set.of(), "UTC"), () -> {
                assertThatThrownBy(() -> batches.get(result.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                assertThatThrownBy(() -> batches.migrate(command)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class); return null;
            });
            assertThat(result.results()).filteredOn(BatchItem::passed).hasSize(1); assertThat(result.pendingInstanceIds()).isEmpty();
            var migrated = service.get(one.id(), "INSTANCE");
            assertThat(((Instance) migrated.draft()).templateVersionId()).isEqualTo(nextVersion);
            assertThat(service.execute(new RuleExecutionApi.Request(one.id(), Map.of("amount", 100))).output()).isEqualTo(new BigDecimal("90.00"));
            assertThat(batches.migrate(command)).isEqualTo(result);
            assertThat(service.get(one.id(), "INSTANCE").revision()).isEqualTo(migrated.revision());
            assertThatThrownBy(() -> batches.migrate(new BatchMigration(command.operationKey(), List.of(command.items().getFirst())))).isInstanceOf(BizException.class);
            var interrupted = new com.sma.admin.rule.internal.persistence.MigrationBatchEntity();
            var recoveryCommand = new BatchMigration("batch_recovery_fixture", command.items());
            interrupted.setPortal("admin"); interrupted.setOperationKey(recoveryCommand.operationKey());
            interrupted.setRequestHash(json.hash(json.write(recoveryCommand))); interrupted.setState("RUNNING"); interrupted.setReportJson("{}");
            batchRows.insert(interrupted);
            interrupted.setReportJson(json.writeReport(new BatchReport(interrupted.getId(), "RUNNING", 2, false,
                    List.of(result.results().getFirst()), List.of(two.id())))); batchRows.updateById(interrupted);
            assertThat(batches.get(interrupted.getId()).state()).isEqualTo("RECOVERY_REQUIRED");
            assertThat(batches.migrate(recoveryCommand).pendingInstanceIds()).containsExactly(two.id());
            assertThat(service.get(one.id(), "INSTANCE").revision()).isEqualTo(migrated.revision());
            return null;
        });
    }
    @Test void executionBindingUsesThePublishedSnapshotWhileAnotherOperatorEditsItsDraft() {
        var actor = user(5124, 1024);
        var live = Users.as(actor, () -> live("binding_snapshot", template("binding_snapshot_scene"), ".9"));
        Users.as(user(5124, 1025), () -> {
            var old = (Instance) live.draft();
            service.saveInstance(live.id(), new SaveInstance(live.revision(), new Instance(old.code(), old.name(), old.templateVersionId(), old.params(),
                    old.objectType(), "owned-private", old.environment(), old.startAt(), old.endAt()))); return null;
        });
        Users.as(actor, () -> {
            assertThat(service.execute(new RuleExecutionApi.Request(live.id(), Map.of("amount", 100))).output()).isEqualTo(new BigDecimal("90.00")); return null;
        });
    }
    @Test void operationsRecordDisabledAndEffectiveStatesAndKeepReferencedPublishEvidence() {
        var actor = user(5122, 1022);
        Users.as(actor, () -> {
            long version = template("operations"); var template = service.list("TEMPLATE", 1, 20).getFirst();
            var instance = live("operations_live", version, ".9");
            var result = service.execute(new RuleExecutionApi.Request(instance.id(), Map.of("amount", 100)));
            assertThat(result.releaseRevision()).isEqualTo(1);
            var detail = operations.detail(instance.id(), Long.parseLong(result.executionId()));
            assertThat(detail.trace()).isNotEmpty(); assertThat(detail.releaseRevision()).isEqualTo(1);
            service.disable(instance.id(), new Publish(instance.revision(), "operations_disable"));
            var disabled = service.execute(new RuleExecutionApi.Request(instance.id(), Map.of("amount", 100)));
            assertThat(disabled.status()).isEqualTo(RuleExecutionApi.Status.DISABLED); assertThat(disabled.releaseRevision()).isEqualTo(2);
            assertThat(disabled.templateVersionId()).isEqualTo(version); assertThat(disabled.instanceRevision()).isNotNull();
            assertThat(service.executionList(instance.id(), 20)).hasSize(2);
            var future = service.createInstance(new Instance("future", "Future", version, Map.of("ratio", new BigDecimal(".9")),
                    "TEST_OBJECT", "owned-future", "default", Instant.now().plusSeconds(3600), null));
            service.trial(future.id(), new Trial(future.revision(), Map.of("amount", 100)));
            service.publishInstance(future.id(), new Publish(future.revision(), "operations_future"));
            assertThat(service.execute(new RuleExecutionApi.Request(future.id(), Map.of("amount", 100))).status()).isEqualTo(RuleExecutionApi.Status.NOT_EFFECTIVE);
            var stats = operations.statistics(instance.id(), Instant.now().minusSeconds(3600), Instant.now().plusSeconds(1));
            assertThat(stats).hasSize(2);
            var search = new ExecutionSearch(null, template.id(), version, RuleExecutionApi.Status.SUCCESS,
                    Instant.now().minusSeconds(3600), Instant.now().plusSeconds(1), 1, 20);
            assertThat(operations.search(search)).hasSize(1).first().satisfies(row -> assertThat(row.id()).isEqualTo(result.executionId()));
            assertThat(operations.search(new ExecutionSearch(null, template.id(), version, RuleExecutionApi.Status.DISABLED,
                    search.from(), search.until(), 1, 20))).hasSize(1);
            Users.as(new SmaPrincipal(9999L, "other-self", actor.tenantId(), "admin", 10L, false, DataAccess.own(), Set.of(), "UTC"), () -> {
                assertThat(operations.search(new ExecutionSearch(null, null, version, null, search.from(), search.until(), 1, 20))).isEmpty();
                assertThatThrownBy(() -> operations.detail(instance.id(), Long.parseLong(result.executionId()))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                return null;
            });
            assertThatThrownBy(() -> operations.statistics(instance.id(), Instant.EPOCH, Instant.now())).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> operations.cleanup(new Retention(1, 100))).isInstanceOf(BizException.class);
            var unreferenced = tests.run(template.id(), new RunInput(version, null));
            jdbc.update("UPDATE rule_testrun SET created_at=DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY) WHERE tenant_id=?", actor.tenantId());
            jdbc.update("UPDATE rule_execution SET created_at=DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY) WHERE id=?", Long.parseLong(result.executionId()));
            var cleaned = operations.cleanup(new Retention(7, 500));
            assertThat(cleaned.executions()).isEqualTo(1); assertThat(cleaned.testRuns()).isEqualTo(1);
            assertThat(tests.runs(template.id(), 20)).noneMatch(run -> run.id() == unreferenced.id());
            assertThat(tests.runs(template.id(), 20)).anyMatch(run -> run.id() == service.versions(template.id()).getFirst().testRunId());
            assertThat(service.executionList(instance.id(), 20)).hasSize(1);
            assertThat(operations.resources().get("expressions")).isPositive();
            operations.clearResources(); assertThat(operations.resources().get("expressions")).isZero();
            return null;
        });
        Users.as(user(5122, 9999), () -> {
            var own = new SmaPrincipal(9999L, "self-only", 5122L, "admin", 10L, false, DataAccess.own(), Set.of(), "UTC");
            Users.as(own, () -> {
                assertThatThrownBy(() -> operations.cleanup(new Retention(7, 50))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                assertThat(service.list("INSTANCE", 1, 20)).isEmpty(); return null;
            }); return null;
        });
    }
    @Test void lifecycleDetectsTransitiveImpactAndRevocationBlocksParentsAndCorruptVersions() {
        Users.as(user(5123, 1023), () -> {
            long child = template("lifecycle_child"); var childAsset = service.list("TEMPLATE", 1, 20).getFirst();
            var direct = live("lifecycle_direct", child, ".9");
            var scene = service.createScene(new Scene("lifecycle_flow", "Flow", input(), output(), params(), List.of(RuleDefinition.Kind.FLOW)));
            var nodes = List.of(new RuleDefinition.Node("start", RuleDefinition.NodeKind.START, null, null, "child", null, null, null, null, null, null),
                    new RuleDefinition.Node("child", RuleDefinition.NodeKind.SUBRULE, null, null, "result", null, null, child, Map.of("amount", "input.amount"), Map.of("ratio", "params.ratio"), output()),
                    new RuleDefinition.Node("result", RuleDefinition.NodeKind.RESULT, "context.steps.child", null, null, null, null, null, null, null, null));
            var doc = new RuleTemplateDocument(1, input(), params(), output(), new RuleDefinition(RuleDefinition.Kind.FLOW, null, null, null, null,
                    new RuleDefinition.Flow("start", nodes)), Map.of(), List.of(new RuleTemplateDocument.Example("normal", Map.of("amount", 100), Map.of("ratio", new BigDecimal(".9")), 90)));
            var parent = service.createTemplate(new Template("lifecycle_parent", "Parent", scene.id(), doc));
            long parentVersion = service.publishTemplate(parent.id(), new Publish(parent.revision(), "lifecycle_parent_publish")).versionId();
            var indirect = live("lifecycle_indirect", parentVersion, ".9");
            var impact = impacts.impact(childAsset.id());
            assertThat(impact.truncated()).isFalse();
            assertThat(impact.assets()).extracting(ImpactAsset::id).contains(direct.id(), parent.id(), indirect.id());
            var deprecated = service.lifecycle(childAsset.id(), new Publish(childAsset.revision(), "lifecycle_deprecate"), "DEPRECATE");
            assertThat(service.lifecycle(childAsset.id(), new Publish(childAsset.revision(), "lifecycle_deprecate"), "DEPRECATE")).isEqualTo(deprecated);
            assertThatThrownBy(() -> service.createInstance(instance("lifecycle_new", child, ".9"))).isInstanceOf(BizException.class);
            assertThat(service.execute(new RuleExecutionApi.Request(direct.id(), Map.of("amount", 100))).status()).isEqualTo(RuleExecutionApi.Status.SUCCESS);
            assertThat(service.execute(new RuleExecutionApi.Request(indirect.id(), Map.of("amount", 100))).status()).isEqualTo(RuleExecutionApi.Status.SUCCESS);
            var current = service.get(childAsset.id(), "TEMPLATE");
            service.lifecycle(childAsset.id(), new Publish(current.revision(), "lifecycle_revoke"), "REVOKE");
            assertThat(service.execute(new RuleExecutionApi.Request(direct.id(), Map.of("amount", 100))).status()).isEqualTo(RuleExecutionApi.Status.ERROR);
            assertThat(service.execute(new RuleExecutionApi.Request(indirect.id(), Map.of("amount", 100))).status()).isEqualTo(RuleExecutionApi.Status.ERROR);
            var original = jdbc.queryForObject("SELECT content_hash FROM rule_version WHERE id=?", String.class, parentVersion);
            jdbc.update("UPDATE rule_version SET content_hash=? WHERE id=?", "0".repeat(64), parentVersion);
            assertThat(service.execute(new RuleExecutionApi.Request(indirect.id(), Map.of("amount", 100))).status()).isEqualTo(RuleExecutionApi.Status.ERROR);
            jdbc.update("UPDATE rule_version SET content_hash=? WHERE id=?", original, parentVersion);
            assertThat(service.executionList(indirect.id(), 20)).hasSize(3);
            return null;
        });
    }
    @Test void httpAuthorizationAndRawJsonKeepDecimalPrecisionAndRejectUnknownFields() {
        var body = new Simulate(document("round(input.amount * params.ratio, 2)"), Map.of("amount", new BigDecimal("300.15")), Map.of("ratio", new BigDecimal("0.9")));
        Users.as(user(5107, 1007, "rule:simulation:run"), () -> {
            var actor=CurrentUser.require();
            assertThat(mvc.post().uri("/api/rule/simulations").with(Users.request(actor)).contentType(MediaType.APPLICATION_JSON).content(json.write(body)))
                    .hasStatusOk().bodyJson().hasPathSatisfying("$.data.status", value -> value.assertThat().isEqualTo("SUCCESS"))
                    .hasPathSatisfying("$.data.output", value -> value.assertThat().isEqualTo(270.14));
            assertThat(mvc.post().uri("/api/rule/templates").with(Users.request(actor)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .hasStatus(403);
            assertThat(mvc.post().uri("/api/rule/simulations").with(Users.request(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"unexpected\":true}"))
                    .hasStatusOk().bodyJson().hasPathSatisfying("$.error", value -> value.assertThat().isEqualTo("rule.error.invalid"));
            return null;
        });
    }
    @Test void nativePublicationReplaysItsOwnReleaseAndKeepsFailedCandidateReports() throws Exception {
        var actor=user(5198,1098,"rule:template:publish");
        long[] templates=Users.as(actor,()->{
            var scene=service.createScene(new Scene("native_scene","Native scene",input(),output(),params(),List.of(RuleDefinition.Kind.EXPRESSION)));
            var good=service.createTemplate(new Template("native_good","Good",scene.id(),document("round(input.amount * params.ratio, 2)")));
            var bad=service.createTemplate(new Template("native_bad","Bad",scene.id(),document("round(input.amount * params.ratio, 2) + 1")));
            return new long[]{good.id(),bad.id()};
        });
        var command=new Publish(1,"native_publication_original");
        var first=nativePublish(actor,templates[0],command);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(first.getResponse().getHeader("X-Request-Policy")).isEqualTo("NATIVE");
        assertThat(first.getResponse().getHeader("X-Operation-Id")).isNull();
        var mapper=new tools.jackson.databind.json.JsonMapper();
        String version=mapper.readTree(first.getResponse().getContentAsString()).at("/data/versionId").asText();
        assertThat(version).isNotBlank();
        Thread.sleep(600); // Preserve the native operationKey after the default HTTP cooldown.
        var replay=nativePublish(actor,templates[0],command);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(replay.getResponse().getContentAsString()).at("/data/versionId").asText()).isEqualTo(version);
        var failed=nativePublish(actor,templates[1],new Publish(1,"native_publication_failed"));
        assertThat(mapper.readTree(failed.getResponse().getContentAsString()).at("/error").asText()).isEqualTo("rule.error.invalid");
        Users.as(actor,()->{
            assertThat(tests.runs(templates[0],10)).hasSize(1);
            assertThat(tests.runs(templates[1],10)).singleElement().satisfies(report->assertThat(report.passed()).isFalse());
            assertThat(service.versions(templates[1])).isEmpty();return null;
        });
    }
    private org.springframework.test.web.servlet.MvcResult nativePublish(SmaPrincipal actor,long id,Publish command) {
        return Users.as(actor,()->{
            try{return rawMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rule/templates/"+id+"/publish")
                    .with(Users.request(actor)).contentType(MediaType.APPLICATION_JSON).content(json.write(command))).andReturn();}
            catch(Exception ex){throw new IllegalStateException(ex);}
        });
    }

    @Test void realNetworkAuthenticatedWorkflowCreatesPublishesExecutesAndAudits() throws Exception {
        record NetworkLogin(com.sma.admin.iam.AccountView account,String token){}
        var owner=new SmaPrincipal(1099,"network-fixture",5199,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
        var login=Users.as(owner,()->{
        var domain=new com.sma.admin.tenant.internal.persistence.DomainEntity();domain.setOwnerTenantId(5199L);domain.setHostname(NETWORK_HOST);domain.setPortal("admin");
        domain.setStatus("ACTIVE");domain.setPrimaryDomain(false);domain.setVerificationStatus("VERIFIED");domain.setTlsStatus("READY");domain.setDnsStatus("READY");domain.setVerifiedAt(java.time.Instant.now());domain.setTlsValidUntil(java.time.Instant.now().plusSeconds(86400));domain.setProvenance("TEST");domains.insert(domain);
        permissions.sync(catalog.permissions());
        var role = roles.save(null, new com.sma.admin.iam.internal.service.RoleService.Input(0L, "Rule network role", "rule-network-role", 0, true, DataScope.ALL, Set.of(), null));
        var ids = permissions.list().stream().filter(item -> item.permissionKey().startsWith("rule:"))
                .map(com.sma.admin.iam.internal.service.PermissionService.View::id).collect(java.util.stream.Collectors.toSet());
        grants.replace(role.id(), ids);
        users.create(new com.sma.admin.iam.internal.service.UserService.Create(
                new com.sma.admin.iam.internal.service.UserService.Input("rule-network-user", "Rule network", null, null, null,
                        "UNSPECIFIED", true, Set.of(role.id()), Set.of(), "en-US", "UTC"), "Q734749c7b"));
        var account = users.authenticate("rule-network-user", "Q734749c7b", "admin").orElseThrow();
        String token = issuer.issue(account, false, java.time.Duration.ofMinutes(10), "127.0.0.1", "rule-network-test").token();
        return new NetworkLogin(account,token);
        });
        var account=login.account();String token=login.token();
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        var sceneBody = new Scene("network_scene", "Network scene", input(), output(), params(), List.of(RuleDefinition.Kind.EXPRESSION));
        long sceneId = network(client, token, "/scenes", sceneBody).path("data").path("id").asLong();
        long templateId = network(client, token, "/templates", new Template("network_template", "Network template", sceneId,
                document("round(input.amount * params.ratio, 2)"))).path("data").path("id").asLong();
        long version = network(client, token, "/templates/" + templateId + "/publish", new Publish(1, "network_template_publish"))
                .path("data").path("versionId").asLong();
        long instanceId = network(client, token, "/instances", instance("network_instance", version, "0.9"))
                .path("data").path("id").asLong();
        network(client, token, "/instances/" + instanceId + "/trial", new Trial(1, Map.of("amount", new BigDecimal("300.15"))));
        network(client, token, "/instances/" + instanceId + "/publish", new Publish(1, "network_instance_publish"));
        var result = network(client, token, "/instances/" + instanceId + "/execute", new Execute(Map.of("amount", new BigDecimal("300.15"))));
        assertThat(result.path("data").path("status").asString()).isEqualTo("SUCCESS");
        assertThat(result.path("data").path("output").decimalValue()).isEqualByComparingTo("270.14");
        assertThat(Users.as(user(5199, account.id()), () -> service.executionList(instanceId, 20))).hasSize(1);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(Users.as(user(5199,account.id()),()->auditLogs.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sma.admin.audit.internal.persistence.OperationLogEntity>()
                        .eq(com.sma.admin.audit.internal.persistence.OperationLogEntity::getUserId, account.id()))))
                        .anyMatch(log -> log.getAction().equals("rule:instance:publish") && log.getResultCode() == 0));
    }
    private tools.jackson.databind.JsonNode network(java.net.http.HttpClient client, String token, String path, Object body) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://" + NETWORK_HOST + ":" + port + "/api/rule" + path))
                .timeout(java.time.Duration.ofSeconds(20)).header("Authorization", "Bearer " + token)
                .header("Idempotency-Key",java.util.UUID.randomUUID().toString())
                .header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.write(body))).build();
        long networkStarted = System.nanoTime();
        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (path.endsWith("/execute")) System.out.println("RULE_HTTP_EXECUTION_SAMPLE_MICROS=" + (System.nanoTime() - networkStarted) / 1000);
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        var node = new tools.jackson.databind.json.JsonMapper().readTree(response.body());
        assertThat(node.path("code").asInt()).as(path + " response: " + response.body()).isZero();
        return node;
    }
}
