package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;
import com.sma.admin.config.internal.service.SharedConfigurationService.*;

@IntegrationTest
@Import(SharedConfigurationTests.CatalogConfiguration.class)
@TestPropertySource(properties="sma.tenant.platform-admin-user-ids=991,992")
class SharedConfigurationTests {
    @Autowired SharedConfigurationService shared;
    @Autowired SharedHeadMapper heads;
    @Autowired SharedRevisionMapper revisions;
    @Autowired ConfigurationPlatformGuard access;
    @Autowired JsonMapper json;
    @Autowired DictionaryCodeRegistry dictionaryCodes;
    @Autowired PlatformTransactionManager transactions;
    private static final Set<String> PERMISSIONS = Set.of("config:shared:view", "config:shared:edit", "config:shared:publish");
    private static final SmaPrincipal OPERATOR = actor(991, 1, PERMISSIONS);
    private static SmaPrincipal actor(long user, long tenant, Set<String> permissions) { return new SmaPrincipal(user, "shared-test", tenant, "admin", null, false, DataAccess.unrestricted(), permissions, "UTC"); }
    private static ConfigurationLabel label(String name) { return new ConfigurationLabel(name, null, Map.of()); }
    private static ParameterDefinition definition(String suffix, String value, int maxLength) {
        return new ParameterDefinition("config", "config.s5." + suffix, "basic", label("Shared " + suffix), ParameterDefinition.Scope.PLATFORM_SHARED,
                ParameterDefinition.ValueType.STRING, ParameterDefinition.OverridePolicy.OVERRIDABLE, value,
                new ParameterDefinition.Constraints(false, false, maxLength, null, null, Set.of()), false, ParameterDefinition.PublicProjection.NONE);
    }
    private static DictionaryDefinition.Item item(String code, String parent, boolean enabled) { return new DictionaryDefinition.Item(code, parent, label(code), enabled, 0); }
    record Catalog(List<ParameterDefinition> parameters, List<DictionaryDefinition> dictionaries) implements ConfigurationCatalog {}
    @TestConfiguration(proxyBeanMethods=false) static class CatalogConfiguration {
        @Bean ConfigurationCatalog sharedCatalog() {
            var parameters = List.of("draft", "concurrent", "snapshot", "rollback", "capacity", "startup", "authority").stream().map(key -> definition(key, "seed", 4000)).toList();
            var dictionary = new DictionaryDefinition("config", "config_s5_tree", label("Shared tree"), DictionaryDefinition.Mode.EXTENDABLE, true,
                    List.of(item("root", null, true), item("child", "root", true)));
            return new Catalog(parameters, List.of(dictionary));
        }
    }
    private Detail detail(String suffix) {
        var page = shared.page(new PageQuery(1, 20, null, null), Kind.PARAMETER, "config.s5." + suffix);
        return shared.get(page.items().stream().filter(item -> item.key().equals("config.s5." + suffix)).findFirst().orElseThrow().id());
    }
    private void host(Runnable work) {
        // Spring's web test thread has request attributes even without authentication. Startup work has neither.
        try (var pool = Executors.newSingleThreadExecutor()) { pool.submit(work).get(30, TimeUnit.SECONDS); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException(failure.getCause());
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    @Test void platformDraftAndPublicationAreSeparateAndCustomersReadOnlyPublishedValues() {
        long id = Users.as(OPERATOR, () -> {
            var before = detail("draft"); assertThat(before.publishedRevision()).isEqualTo(1);
            var saved = shared.saveParameter(before.id(), before.draftVersion(), new ParameterContent(1, label("Edited draft"), "draft only"));
            assertThat(saved.draftVersion()).isEqualTo(2); assertThat(saved.publishedRevisionId()).isEqualTo(before.publishedRevisionId());
            assertThat(shared.published(Kind.PARAMETER, before.key()).orElseThrow().parameter().value()).isEqualTo("seed");
            var result = shared.publish(before.id(), saved.draftVersion(), saved.publishedRevisionId()); assertThat(result.revision()).isEqualTo(2);
            assertThatThrownBy(() -> shared.publish(before.id(), saved.draftVersion(), saved.publishedRevisionId())).isInstanceOf(BizException.class);
            assertThat(shared.revision(before.id(), before.publishedRevisionId()).parameter().value()).isEqualTo("seed");
            return before.id();
        });
        Users.as(actor(300, 3300, Set.of()), () -> {
            var published = shared.published(Kind.PARAMETER, "CONFIG.S5.DRAFT").orElseThrow();
            assertThat(published.parameter().value()).isEqualTo("draft only"); assertThat(published.revision()).isEqualTo(2);
            assertThatThrownBy(() -> shared.get(id)).isInstanceOf(AccessDeniedException.class); return null;
        });
        Users.as(actor(991, 1, Set.of("config:shared:view")), () -> {
            var view = shared.get(id);
            assertThatThrownBy(() -> shared.saveParameter(id, view.draftVersion(), new ParameterContent(1, label("Denied"), "denied"))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> shared.publish(id, view.draftVersion(), view.publishedRevisionId())).isInstanceOf(AccessDeniedException.class); return null;
        });
    }

    @Test void publicationAndDraftWritesRollbackTogetherWithoutLeavingHistory() {
        Users.as(OPERATOR, () -> {
            var before = detail("rollback");
            assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
                var draft = shared.saveParameter(before.id(), before.draftVersion(), new ParameterContent(1, label("Rollback"), "must not commit"));
                shared.publish(before.id(), draft.draftVersion(), draft.publishedRevisionId()); throw new IllegalStateException("Rollback fixture");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(shared.get(before.id()).draftVersion()).isEqualTo(before.draftVersion());
            assertThat(shared.get(before.id()).publishedRevisionId()).isEqualTo(before.publishedRevisionId());
            assertThat(shared.history(before.id(), new PageQuery(1, 20, null, null)).total()).isEqualTo(1); return null;
        });
    }

    @Test void concurrentPublishersCannotCommitTheSameExpectedPointerTwice() throws Exception {
        var before = Users.as(OPERATOR, () -> detail("concurrent")); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (long user : List.of(991L, 992L)) results.add(pool.submit(() -> { start.await(); return Users.as(actor(user, 1, PERMISSIONS), () -> {
                try { shared.publish(before.id(), before.draftVersion(), before.publishedRevisionId()); return true; }
                catch (BizException conflict) { assertThat(conflict.getErrorCode()).isEqualTo(ConfigErrorCode.SHARED_STALE); return false; }
            }); }));
            start.countDown(); int success = 0; for (var result : results) if (result.get(30, TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(1);
        }
        Users.as(OPERATOR, () -> { assertThat(shared.history(before.id(), new PageQuery(1, 20, null, null)).total()).isEqualTo(2); return null; });
    }

    @Test void oldSnapshotsDoNotRestoreAnOlderDraftOrPublishedPointer() {
        Users.as(OPERATOR, () -> {
            var before = detail("snapshot");
            new TransactionTemplate(transactions).execute(status -> {
                assertThat(shared.get(before.id()).draftVersion()).isEqualTo(1); // Establish an old consistent-read snapshot.
                Publication other;
                try (var pool = Executors.newSingleThreadExecutor()) {
                    other = pool.submit(() -> Users.as(actor(992, 1, PERMISSIONS), () -> {
                        var saved = shared.saveParameter(before.id(), 1, new ParameterContent(1, label("Other"), "other"));
                        return shared.publish(before.id(), saved.draftVersion(), saved.publishedRevisionId());
                    })).get(30, TimeUnit.SECONDS);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
                var current = shared.saveParameter(before.id(), 2, new ParameterContent(1, label("Latest"), "latest"));
                assertThat(current.publishedRevisionId()).isEqualTo(other.revisionId());
                assertThat(shared.publish(before.id(), current.draftVersion(), current.publishedRevisionId()).revision()).isEqualTo(3);
                return null;
            });
            assertThat(shared.published(Kind.PARAMETER, before.key()).orElseThrow().parameter().value()).isEqualTo("latest");
            assertThat(shared.history(before.id(), new PageQuery(1, 20, null, null)).total()).isEqualTo(3); return null;
        });
    }

    @Test void dictionaryCodesAndParentsStayStableAndOversizedDraftsDoNotSave() {
        Users.as(OPERATOR, () -> {
            var head = shared.page(new PageQuery(1, 20, null, null), Kind.DICTIONARY, "config_s5_tree").items().getFirst();
            var before = shared.get(head.id());
            assertThatThrownBy(() -> shared.saveDictionary(head.id(), 1, new DictionaryContent(1, label("Bad"), List.of(item("root", null, true))))).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> shared.saveDictionary(head.id(), 1, new DictionaryContent(1, label("Bad"), List.of(item("root", null, true), item("child", null, true))))).isInstanceOf(BizException.class);
            var huge = new ArrayList<>(before.dictionaryDraft().items());
            for (int i = 0; i < 510; i++) huge.add(new DictionaryDefinition.Item("n" + i, "root", new ConfigurationLabel("文".repeat(128), null, Map.of("zh-CN", "文".repeat(128), "en-US", "x".repeat(128))), true, i));
            assertThatThrownBy(() -> shared.saveDictionary(head.id(), 1, new DictionaryContent(1, label("Huge"), huge))).isInstanceOf(BizException.class);
            assertThat(shared.get(head.id()).draftVersion()).isEqualTo(1);
            var saved = shared.saveDictionary(head.id(), 1, new DictionaryContent(1, label("Changed"), List.of(item("root", null, true), item("child", "root", false), item("new", "root", true))));
            shared.publish(head.id(), saved.draftVersion(), saved.publishedRevisionId());
            assertThat(shared.revision(head.id(), before.publishedRevisionId()).dictionary().items().get(1).enabled()).isTrue();
            assertThat(shared.published(Kind.DICTIONARY, "config_s5_tree").orElseThrow().dictionary().items().get(1).enabled()).isFalse(); return null;
        });
    }

    @Test @Transactional void historyCapacityRejectsWithoutMovingThePointer() {
        Users.as(OPERATOR, () -> {
            var before = detail("capacity"); var row = heads.lock(before.id()); row.setHistoryBytes(SharedConfigurationService.MAX_HISTORY_BYTES); heads.updateById(row);
            assertThatThrownBy(() -> shared.publish(before.id(), before.draftVersion(), before.publishedRevisionId())).isInstanceOfSatisfying(BizException.class, failure -> assertThat(failure.getErrorCode()).isEqualTo(ConfigErrorCode.SHARED_CAPACITY));
            assertThat(shared.get(before.id()).publishedRevisionId()).isEqualTo(before.publishedRevisionId()); return null;
        });
    }

    @Test void hostReinitializationPreservesEditsAndDefaultChangesWhileContractDriftFails() {
        var before = Users.as(OPERATOR, () -> {
            var current = detail("startup"); return shared.saveParameter(current.id(), current.draftVersion(), new ParameterContent(1, label("Kept draft"), "kept"));
        });
        host(() -> shared.initialize(Kind.PARAMETER, before.key()));
        var currentCode = new ConfigurationCatalogRegistry(List.of(new Catalog(List.of(definition("startup", "new code default", 4000)), List.of())), json);
        var updatedCodeService = new SharedConfigurationService(heads, revisions, currentCode, access, json,dictionaryCodes);
        host(() -> new TransactionTemplate(transactions).execute(status -> { updatedCodeService.initialize(Kind.PARAMETER, before.key()); return null; }));
        Users.as(OPERATOR, () -> {
            assertThat(updatedCodeService.get(before.id()).codeDefaultsChanged()).isTrue();
            assertThat(updatedCodeService.get(before.id()).parameterDraft().value()).isEqualTo("kept");
            assertThat(shared.get(before.id()).publishedRevisionId()).isEqualTo(before.publishedRevisionId());
            assertThatThrownBy(() -> shared.initialize(Kind.PARAMETER, before.key())).isInstanceOf(AccessDeniedException.class); return null;
        });
        var incompatible = new ConfigurationCatalogRegistry(List.of(new Catalog(List.of(definition("startup", "new code default", 3999)), List.of())), json);
        var incompatibleService = new SharedConfigurationService(heads, revisions, incompatible, access, json,dictionaryCodes);
        assertThatThrownBy(() -> host(() -> new TransactionTemplate(transactions).execute(status -> { incompatibleService.initialize(Kind.PARAMETER, before.key()); return null; }))).isInstanceOf(IllegalStateException.class).hasMessageContaining("contract changed");
    }

    @Test void simultaneousHostRegistrationCreatesOneHeadAndOneInitialPublication() throws Exception {
        var registry = new ConfigurationCatalogRegistry(List.of(new Catalog(List.of(definition("node-race", "seed", 4000)), List.of())), json);
        var startupService = new SharedConfigurationService(heads, revisions, registry, access, json,dictionaryCodes); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> work = new ArrayList<>();
            for (int i = 0; i < 2; i++) work.add(pool.submit(() -> { start.await(); return new TransactionTemplate(transactions).execute(status -> { startupService.initialize(Kind.PARAMETER, "config.s5.node-race"); return null; }); }));
            start.countDown(); for (var future : work) future.get(30, TimeUnit.SECONDS);
        }
        Users.as(OPERATOR, () -> {
            var result = startupService.page(new PageQuery(1, 20, null, null), Kind.PARAMETER, null);
            assertThat(result.total()).isEqualTo(1); assertThat(startupService.history(result.items().getFirst().id(), new PageQuery(1, 20, null, null)).total()).isEqualTo(1); return null;
        });
    }
}
