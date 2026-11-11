package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;
import com.sma.admin.support.*;

@IntegrationTest @Import(DefinedParameterTests.CatalogConfiguration.class)
@TestPropertySource(properties="sma.tenant.platform-admin-user-ids=991")
class DefinedParameterTests {
    @Autowired DefinedParameterService service;
    @Autowired SharedConfigurationService shared;
    @Autowired ParameterMapper parameters;
    @Autowired ConfigNames names;
    @Autowired WriteLockMapper locks;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DefinedParameterReader reader;
    @Autowired com.sma.admin.common.cache.ManagedCache cache;
    private static final String KEY="config.layers.value";
    private static final Set<String> EDIT=Set.of("config:parameter:update");
    private static SmaPrincipal actor(long tenant,Set<String> permissions) {
        return new SmaPrincipal(991,"parameter-layers",tenant,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");
    }
    private <T>T as(long tenant,Supplier<T> work) { return Users.as(actor(tenant,EDIT),work); }
    private <T>T host(long tenant,Supplier<T> work) {
        try(var pool=Executors.newSingleThreadExecutor()) { return pool.submit(()->TenantScope.within(tenant,work)).get(30,TimeUnit.SECONDS); }
        catch(ExecutionException failure) { if(failure.getCause() instanceof RuntimeException cause) throw cause; throw new IllegalStateException(failure); }
        catch(Exception failure) { throw new IllegalStateException(failure); }
    }
    private static ParameterDefinition definition(String key,ParameterDefinition.Scope scope,ParameterDefinition.OverridePolicy policy) {
        return new ParameterDefinition("config",key,"basic",new ConfigurationLabel(key,null,Map.of("en-US","English")),scope,
                ParameterDefinition.ValueType.STRING,policy,"seed",new ParameterDefinition.Constraints(true,true,100,null,null,Set.of()),false,ParameterDefinition.PublicProjection.NONE);
    }
    record Catalog(List<ParameterDefinition> parameters) implements ConfigurationCatalog {}
    @TestConfiguration(proxyBeanMethods=false) static class CatalogConfiguration {
        @Bean ConfigurationCatalog parameterLayersCatalog() { return new Catalog(List.of(
                definition(KEY,ParameterDefinition.Scope.PLATFORM_SHARED,ParameterDefinition.OverridePolicy.OVERRIDABLE),
                definition("config.layers.publication",ParameterDefinition.Scope.PLATFORM_SHARED,ParameterDefinition.OverridePolicy.OVERRIDABLE),
                definition("config.layers.cached",ParameterDefinition.Scope.PLATFORM_SHARED,ParameterDefinition.OverridePolicy.OVERRIDABLE),
                definition("config.layers.pinned",ParameterDefinition.Scope.PLATFORM_SHARED,ParameterDefinition.OverridePolicy.OVERRIDABLE),
                new ParameterDefinition("config","config.layers.security","security",new ConfigurationLabel("Security",null,Map.of()),
                    ParameterDefinition.Scope.PLATFORM_SHARED,ParameterDefinition.ValueType.BOOLEAN,ParameterDefinition.OverridePolicy.OVERRIDABLE,"true",
                    ParameterDefinition.Constraints.required(),true,ParameterDefinition.PublicProjection.NONE),
                definition("config.layers.locked",ParameterDefinition.Scope.PLATFORM_SHARED,ParameterDefinition.OverridePolicy.LOCKED),
                definition("config.layers.private",ParameterDefinition.Scope.TENANT_ONLY,ParameterDefinition.OverridePolicy.OVERRIDABLE))); }
    }
    @Test void newTenantInheritsWithoutRowsAndExplicitNullEmptyAndRestoreRemainDistinct() {
        as(7101,()->{
            var initial=service.get(KEY); assertThat(initial.rowId()).isNull(); assertThat(initial.snapshot().origin()).isEqualTo(ConfigurationOrigin.PLATFORM);
            assertThat(initial.snapshot().value()).isEqualTo("seed");
            var empty=service.override(KEY,initial.snapshot().revision(),""); assertThat(empty.snapshot().value()).isEmpty();
            var nil=service.override(KEY,empty.snapshot().revision(),null);
            assertThat(service.get(KEY).snapshot().value()).isNull(); assertThat(nil.overridden()).isTrue();
            var inherited=service.inherit(KEY,nil.snapshot().revision());
            assertThat(inherited.rowId()).isEqualTo(empty.rowId()); assertThat(inherited.snapshot().value()).isEqualTo("seed");
            assertThat(inherited.snapshot().revision().tenant()).isGreaterThan(nil.snapshot().revision().tenant());
            assertThatThrownBy(()->service.override(KEY,initial.snapshot().revision(),"stale")).isInstanceOf(BizException.class);
            as(7102,()->{assertThat(service.get(KEY).rowId()).isNull(); assertThat(service.get(KEY).snapshot().revision().tenant()).isZero(); return null;});
            return null;
        });
    }
    @Test void lockedDefinitionsAndMissingHumanPermissionCannotWrite() {
        as(7103,()->{
            var locked=service.get("config.layers.locked");
            assertThatThrownBy(()->service.override("config.layers.locked",locked.snapshot().revision(),"bad")).isInstanceOf(BizException.class);
            var privateValue=service.get("config.layers.private"); assertThat(privateValue.snapshot().revision().platform()).isZero();
            var updated=service.override("config.layers.private",privateValue.snapshot().revision(),"tenant-only");
            assertThat(updated.snapshot().origin()).isEqualTo(ConfigurationOrigin.TENANT_ONLY);
            assertThat(service.inherit("config.layers.private",updated.snapshot().revision()).snapshot().origin()).isEqualTo(ConfigurationOrigin.DEFAULT);
            return null;
        });
        Users.as(actor(7103,Set.of()),()->{
            var current=service.get(KEY);
            assertThatThrownBy(()->service.override(KEY,current.snapshot().revision(),"denied")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(service::migrateCurrentTenant).isInstanceOf(AccessDeniedException.class); return null;
        });
    }
    @Test void concurrentWritesWithTheSameVersionHaveOneWinnerAndRollbackPreservesRevision() throws Exception {
        var initial=as(7104,()->service.get(KEY)); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<2;i++) results.add(pool.submit(()->{start.await();return as(7104,()->{
                try {service.override(KEY,initial.snapshot().revision(),"winner");return true;}
                catch(BizException failure) {assertThat(failure.getErrorCode()).isEqualTo(ConfigErrorCode.SHARED_STALE);return false;}
            });}));
            start.countDown(); int winners=0; for(var result:results) if(result.get(30,TimeUnit.SECONDS)) winners++;
            assertThat(winners).isEqualTo(1);
        }
        as(7104,()->{
            var before=service.get(KEY);
            assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
                service.override(KEY,before.snapshot().revision(),"rollback"); throw new IllegalStateException("fixture rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(service.get(KEY).snapshot()).isEqualTo(before.snapshot()); return null;
        });
    }
    @Test void oldRepeatableReadSnapshotStillObservesCurrentTenantValue() {
        as(7105,()->{
            service.override(KEY,service.get(KEY).snapshot().revision(),"first");
            new TransactionTemplate(transactions).execute(status->{
                // Establish an ordinary consistent-read snapshot without taking the configuration guard.
                parameters.selectById(serviceRowId(7105));
                host(7105,()->Users.as(actor(7105,EDIT),()->{
                    var current=service.get(KEY);service.override(KEY,current.snapshot().revision(),"latest");return null;
                }));
                assertThat(service.get(KEY).snapshot().value()).isEqualTo("latest"); return null;
            }); return null;
        });
    }
    private long serviceRowId(long tenant) {
        return host(tenant,()->Users.as(actor(tenant,EDIT),()->service.get(KEY).rowId()));
    }
    @Test void legacyRowsAndTranslationsArePreservedAndMigrationIsIdempotent() {
        long id=as(7106,()->legacy(KEY,"seed",false));
        as(7106,()->{assertThatThrownBy(()->service.get(KEY)).isInstanceOf(BizException.class);return null;});
        assertThat(host(7106,service::migrateCurrentTenant)).isEqualTo(1);
        var migrated=as(7106,()->service.get(KEY));
        assertThat(migrated.rowId()).isEqualTo(id); assertThat(migrated.overridden()).isTrue();
        assertThat(migrated.snapshot().origin()).isEqualTo(ConfigurationOrigin.TENANT_OVERRIDE);
        as(7106,()->{
            var row=parameters.selectById(id); assertThat(row.getName()).isEqualTo("Custom label"); assertThat(row.getSort()).isEqualTo(17);
            assertThat(names.translations("parameter",id)).containsEntry("en-US","Custom translation");
            service.override(KEY,migrated.snapshot().revision(),"edited");return null;
        });
        assertThat(host(7106,service::migrateCurrentTenant)).isZero();
        assertThat(as(7106,()->service.get(KEY).snapshot().value())).isEqualTo("edited");
    }
    @Test void incompatibleLegacyValuesStopMigrationWithoutLeakingOrPartiallyBinding() {
        as(7107,()->{legacy(KEY,"credential-must-not-leak",true);return null;});
        assertThatThrownBy(()->host(7107,service::migrateCurrentTenant)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant=7107").hasMessageContaining(KEY).hasMessageNotContaining("credential-must-not-leak").hasNoCause();
        as(7107,()->{assertThat(parameters.current(KEY).getDefinitionContract()).isNull();assertThat(locks.parameterRevision(7107)).isNull();return null;});
    }
    @Test void sharedPublicationChangesInvalidateExpectedVersionButNeverOverwriteTenantOverride() {
        String key="config.layers.publication";
        var original=as(7108,()->service.get(key));
        var override=as(7109,()->service.override(key,service.get(key).snapshot().revision(),"private"));
        Users.as(actor(1,Set.of("config:shared:view","config:shared:edit","config:shared:publish")),()->{
            var row=shared.page(new PageQuery(1,20,null,null),Kind.PARAMETER,key).items().getFirst();
            var saved=shared.saveParameter(row.id(),row.draftVersion(),new SharedConfigurationService.ParameterContent(1,new ConfigurationLabel("New",null,Map.of()),"published"));
            shared.publish(row.id(),saved.draftVersion(),saved.publishedRevisionId());return null;
        });
        as(7108,()->{
            assertThat(service.get(key).snapshot().value()).isEqualTo("published");
            assertThatThrownBy(()->service.override(key,original.snapshot().revision(),"stale")).isInstanceOf(BizException.class); return null;
        });
        as(7109,()->{
            assertThat(service.get(key).snapshot().value()).isEqualTo("private");
            assertThatThrownBy(()->service.inherit(key,override.snapshot().revision())).isInstanceOf(BizException.class);return null;
        });
    }
    private long legacy(String key,String value,boolean secret) {
        var row=new ParameterEntity();row.setParamKey(key);row.setGroupKey("basic");row.setName("Custom label");row.setValueType("STRING");
        row.setParamValue(value);row.setBuiltin(false);row.setSecret(secret);row.setPublicReadable(false);row.setSort(17);row.setBrandArchived(false);
        parameters.insert(row); names.replace("parameter",row.getId(),Map.of("en-US","Custom translation")); return row.getId();
    }

    @Test void ordinaryReadsActuallyHitTheTenantCacheAndRollbackCannotPoisonIt() {
        as(7110,()->{
            String key="config.layers.cached";
            assertThat(reader.read(key).orElseThrow().value()).isEqualTo("seed");
            assertThat(reader.read(key.toUpperCase(Locale.ROOT)).orElseThrow().value()).isEqualTo("seed");
            assertThat(cache.snapshot(ConfigCacheCatalog.PARAMETERS).hits()).isEqualTo(1);
            var before=service.get(key);
            new TransactionTemplate(transactions).execute(status->{
                service.override(key,before.snapshot().revision(),"uncommitted");
                assertThat(reader.read(key).orElseThrow().value()).isEqualTo("uncommitted");status.setRollbackOnly();return null;
            });
            assertThat(reader.read(key).orElseThrow()).isEqualTo(before.snapshot());
            var saved=service.override(key,before.snapshot().revision(),null);
            assertThat(reader.read(key).orElseThrow().value()).isNull();assertThat(reader.read(key).orElseThrow().value()).isNull();
            assertThat(service.inherit(key,saved.snapshot().revision()).snapshot().value()).isEqualTo("seed");
            assertThat(reader.read(key).orElseThrow().origin()).isEqualTo(ConfigurationOrigin.PLATFORM);
            as(7111,()->{assertThat(reader.read(key).orElseThrow().revision().tenant()).isZero();return null;});
            return null;
        });
    }
    @Test void securityClassificationBypassesCacheEvenForCaseAliases() {
        as(7112,()->{
            String key="config.layers.security";
            assertThat(reader.read(key.toUpperCase(Locale.ROOT)).orElseThrow().value()).isEqualTo("true");
            assertThat(reader.read(key).orElseThrow().value()).isEqualTo("true");
            assertThat(cache.snapshot(ConfigCacheCatalog.PARAMETERS).size()).isZero();
            service.override(key,service.get(key).snapshot().revision(),"false");
            assertThat(reader.read(key).orElseThrow().value()).isEqualTo("false");
            assertThat(cache.snapshot(ConfigCacheCatalog.PARAMETERS).size()).isZero();return null;
        });
    }
    @Test void pinnedPublicationLoadsItsOwnBodyAndAnInterveningTenantWriteCannotUseTheOldCacheAddress() {
        String key="config.layers.private";
        as(7113,()->{
            var frame=service.capture(key);
            service.override(key,frame.revision(),"newer");
            var resolved=service.resolve(frame);assertThat(resolved.value()).isEqualTo("newer");assertThat(resolved.revision()).isNotEqualTo(frame.revision());
            as(7114,()->{assertThatThrownBy(()->service.resolve(frame)).isInstanceOf(BizException.class);return null;});return null;
        });
        String publishedKey="config.layers.pinned";
        var frame=as(7115,()->service.capture(publishedKey));
        Users.as(actor(1,Set.of("config:shared:view","config:shared:edit","config:shared:publish")),()->{
            var row=shared.page(new PageQuery(1,20,null,null),Kind.PARAMETER,publishedKey).items().getFirst();
            var next=shared.saveParameter(row.id(),row.draftVersion(),new SharedConfigurationService.ParameterContent(1,new ConfigurationLabel("Next",null,Map.of()),"next-publication"));
            shared.publish(row.id(),next.draftVersion(),next.publishedRevisionId());return null;
        });
        as(7115,()->{
            assertThat(service.resolve(frame).value()).isEqualTo("seed");
            assertThat(reader.read(publishedKey).orElseThrow().value()).isEqualTo("next-publication");return null;
        });
    }
}
