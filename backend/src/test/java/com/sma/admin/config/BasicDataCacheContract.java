package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.monitor.internal.service.CacheService;
import com.sma.admin.support.Users;

/** Same externally visible contract for Local, None and a real Redis provider. */
abstract class BasicDataCacheContract {
    @Autowired DictionaryService dictionaries;
    @Autowired PreferenceService preferences;
    @Autowired ParameterService parameters;
    @Autowired DefinedParameterService parameterLayers;
    @Autowired ManagedCache cache;
    @Autowired CacheRevisionStore revisions;
    @Autowired CacheService management;
    @Autowired PlatformTransactionManager transactions;
    abstract long tenantBase();

    @Test void repeatReadsActuallyHitStorageExceptWithNoneProvider() {
        Users.as(Users.principal(101, tenantBase()+7, DataAccess.unrestricted()), () -> {
            var type = dictionaries.saveType(null, type("hits", true));
            dictionaries.saveItem(null, item(type.id(), 0L, "one", "一", "One", true));
            preferences.save(preference("amber"));
            for (int i=0; i<2; i++) {
                assertThat(dictionaries.options("hits")).hasSize(1);
                assertThat(preferences.get(101, null, null).skin()).isEqualTo("amber");
            }
            for (var region : List.of(ConfigCacheCatalog.DICTIONARIES, ConfigCacheCatalog.PREFERENCES)) {
                var snapshot = cache.snapshot(region);
                if (snapshot.provider().equals("none")) {
                    assertThat(snapshot.size()).isZero(); assertThat(snapshot.hits()).isZero();
                    assertThat(snapshot.bypasses()).isGreaterThanOrEqualTo(2);
                } else {
                    assertThat(snapshot.size()).isEqualTo(1); assertThat(snapshot.hits()).isPositive();
                }
            }
            return null;
        });
    }

    @Test void dictionaryMissTranslationTreeRenameDisableAndDeletionInvalidateEveryLocale() {
        Users.as(Users.principal(101, tenantBase(), DataAccess.unrestricted()), () -> {
            try {
                LocaleContextHolder.setLocale(Locale.US);
                assertThat(dictionaries.options("tree")).isEmpty();
                var type = dictionaries.saveType(null, type("tree", true));
                var parent = dictionaries.saveItem(null, item(type.id(), 0L, "parent", "父", "Parent", true));
                var child = dictionaries.saveItem(null, item(type.id(), parent.id(), "child", "子", "Child", true));
                var english = dictionaries.options("tree");
                assertThat(english.getFirst().children().getFirst().label()).isEqualTo("Child");
                assertThatThrownBy(() -> english.clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> english.getFirst().children().clear()).isInstanceOf(UnsupportedOperationException.class);
                LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
                assertThat(dictionaries.options("tree").getFirst().children().getFirst().label()).isEqualTo("子");
                dictionaries.saveItem(child.id(), item(type.id(), parent.id(), "child", "新子", "New child", true));
                assertThat(dictionaries.options("tree").getFirst().children().getFirst().label()).isEqualTo("新子");
                LocaleContextHolder.setLocale(Locale.US);
                assertThat(dictionaries.options("tree").getFirst().children().getFirst().label()).isEqualTo("New child");
                dictionaries.saveType(type.id(), type("renamed", true));
                assertThat(dictionaries.options("tree")).isEmpty();
                assertThat(dictionaries.options("renamed")).hasSize(1);
                dictionaries.saveItem(parent.id(), item(type.id(), 0L, "parent", "父", "Parent", false));
                assertThat(dictionaries.options("renamed")).isEmpty();
                dictionaries.saveItem(parent.id(), item(type.id(), 0L, "parent", "父", "Parent", true));
                dictionaries.saveType(type.id(), type("renamed", false));
                assertThat(dictionaries.options("renamed")).isEmpty();
                dictionaries.saveType(type.id(), type("renamed", true));
                assertThat(dictionaries.options("renamed")).hasSize(1);
                Users.as(Users.principal(101, tenantBase()+1, DataAccess.unrestricted()), () -> {
                    assertThat(dictionaries.options("renamed")).isEmpty(); return null;
                });
                dictionaries.deleteItem(child.id());
                assertThat(dictionaries.options("renamed").getFirst().children()).isEmpty();
                dictionaries.deleteItem(parent.id()); dictionaries.deleteType(type.id());
                assertThat(dictionaries.options("renamed")).isEmpty();
                return null;
            } finally { LocaleContextHolder.resetLocaleContext(); }
        });
    }

    @Test void dictionaryRollbackCannotPublishItsSnapshotAndInitializationInvalidatesNegativeEntries() {
        Users.as(Users.principal(101, tenantBase()+2, DataAccess.unrestricted()), () -> {
            assertThat(dictionaries.options("seed")).isEmpty();
            dictionaries.initialize(List.of(new DictionaryService.SeedType("seed", "Seed", null, false,
                    List.of(new DictionaryService.SeedItem("one", "One", null, null)))));
            assertThat(dictionaries.options("seed")).extracting(DictionaryOption::value).containsExactly("one");
            var type = dictionaries.saveType(null, type("rollback", true));
            var item = dictionaries.saveItem(null, item(type.id(), 0L, "original", "原", "Original", true));
            assertThat(dictionaries.options("rollback").getFirst().value()).isEqualTo("original");
            long revision = revisions.current(ConfigCacheCatalog.DICTIONARIES);
            new TransactionTemplate(transactions).execute(status -> {
                dictionaries.saveItem(item.id(), item(type.id(), 0L, "uncommitted", "临时", "Temporary", true));
                assertThat(dictionaries.options("rollback").getFirst().value()).isEqualTo("uncommitted");
                status.setRollbackOnly(); return null;
            });
            assertThat(dictionaries.options("rollback").getFirst().value()).isEqualTo("original");
            assertThat(revisions.current(ConfigCacheCatalog.DICTIONARIES)).isEqualTo(revision);
            return null;
        });
    }

    @Test void preferencesCacheOnlyRawCurrentUserDataAndRecomposeDefaultsOnEveryRead() {
        Users.as(new SmaPrincipal(101,"cache-config-editor",tenantBase()+3,"admin",null,false,DataAccess.unrestricted(),Set.of("config:parameter:update"),"UTC"), () -> {
            assertThat(preferences.get(101, "en-US", "UTC").locale()).isEqualTo("en-US");
            assertThat(preferences.get(101, "zh-CN", "Asia/Shanghai").timeZone()).isEqualTo("Asia/Shanghai");
            parameters.saveGroup(null, new ParameterService.GroupInput("ui", "UI", null, 0, Map.of()));
            var theme=parameterLayers.override("ui.theme",parameterLayers.get("ui.theme").snapshot().revision(),"dark");
            assertThat(preferences.get(101, "en-US", "UTC").theme()).isEqualTo("dark");
            parameterLayers.override("ui.theme",theme.snapshot().revision(),"light");
            assertThat(preferences.get(101, "en-US", "UTC").theme()).isEqualTo("light");
            preferences.save(preference("celadon"));
            assertThat(preferences.get(101, null, null).skin()).isEqualTo("celadon");
            Users.as(Users.principal(102, tenantBase()+3, DataAccess.unrestricted()), () -> {
                preferences.save(preference("amber"));
                assertThat(preferences.get(102, null, null).skin()).isEqualTo("amber"); return null;
            });
            assertThat(preferences.get(102, null, null).skin()).isEqualTo("amber");
            assertThat(preferences.get(101, null, null).skin()).isEqualTo("celadon");
            Users.as(Users.principal(101, tenantBase()+4, DataAccess.unrestricted()), () -> {
                assertThat(preferences.get(101, null, null).skin()).isEqualTo("iris"); return null;
            });
            long revision = revisions.current(ConfigCacheCatalog.PREFERENCES);
            new TransactionTemplate(transactions).execute(status -> {
                preferences.reset();
                assertThat(preferences.get(101, null, null).skin()).isEqualTo("iris");
                preferences.save(preference("amber"));
                assertThat(preferences.get(101, null, null).skin()).isEqualTo("amber");
                status.setRollbackOnly(); return null;
            });
            assertThat(preferences.get(101, null, null).skin()).isEqualTo("celadon");
            assertThat(revisions.current(ConfigCacheCatalog.PREFERENCES)).isEqualTo(revision);
            preferences.reset();
            assertThat(preferences.get(101, "en-US", "UTC").skin()).isEqualTo("iris");
            return null;
        });
    }

    @Test void directoryIncludesNewRegionsAndClearOnlyInvalidatesCurrentTenant() {
        Users.as(cacheManager(tenantBase()+5), () -> {
            dictionaries.options("absent"); preferences.get(101, "en-US", "UTC");
            var before = revisions.current(ConfigCacheCatalog.DICTIONARIES);
            var directory = management.list();
            assertThat(directory).extracting(CacheService.View::id)
                    .contains(ConfigCacheCatalog.PARAMETERS, ConfigCacheCatalog.DICTIONARIES, ConfigCacheCatalog.PREFERENCES);
            assertThat(directory.toString()).doesNotContain("absent", "en-US");
            Users.as(cacheManager(tenantBase()+6), () -> {
                management.clearAll(); return null;
            });
            assertThat(revisions.current(ConfigCacheCatalog.DICTIONARIES)).isEqualTo(before);
            management.clear(ConfigCacheCatalog.DICTIONARIES);
            assertThat(revisions.current(ConfigCacheCatalog.DICTIONARIES)).isGreaterThan(before);
            return null;
        });
    }
    private SmaPrincipal cacheManager(long tenant) {
        return new SmaPrincipal(101,"cache-manager",tenant,"admin",null,false,DataAccess.unrestricted(),Set.of("monitor:cache:clear","monitor:cache:view"),"UTC");
    }
    private DictionaryService.TypeInput type(String key, boolean enabled) {
        return new DictionaryService.TypeInput(key, "Tree", null, true, enabled, 0, Map.of());
    }
    private DictionaryService.ItemInput item(long type, long parent, String value, String name, String english, boolean enabled) {
        return new DictionaryService.ItemInput(type, parent, value, name, null, enabled, 0, Map.of("en-US", english));
    }
    private PreferenceService.Input preference(String skin) {
        return new PreferenceService.Input("en-US", "UTC", "dark", true, false, skin);
    }
}
