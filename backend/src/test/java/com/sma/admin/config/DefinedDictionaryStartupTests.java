package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.BizException;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.service.TenantDirectoryService;

/** Old rows exist before the real ready-time migration; the unchanged DictionaryQuery API then reads effective layers. */
@IntegrationTest @Import(DefinedDictionaryStartupTests.FixtureConfiguration.class)
@TestPropertySource(properties="sma.tenant.platform-admin-user-ids=991")
class DefinedDictionaryStartupTests {
    @Autowired LegacyFixture fixture;
    @Autowired DictionaryQuery query;
    @Autowired EffectiveConfigurationQuery effective;
    @Autowired DictionaryService legacy;
    @Autowired DefinedDictionaryService layers;
    @Autowired DictionaryWarmupLoader warmup;
    @Autowired ManagedCache cache;
    @Autowired PlatformTransactionManager transactions;
    @AfterEach void locale(){LocaleContextHolder.resetLocaleContext();}
    private static List<String> values(List<DictionaryOption> options){var result=new ArrayList<String>();for(var item:options){result.add(item.value());result.addAll(values(item.children()));}return result;}
    private SmaPrincipal actor(long tenant,Set<String> permissions){return new SmaPrincipal(991,"dictionary-startup",tenant,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");}

    @Test void startupBindsLegacyPresentationPrivateCodesAndDisabledTypes() {
        LocaleContextHolder.setLocale(Locale.US);
        Users.as(actor(fixture.tenant,Set.of("config:dictionary:view")),()->{
            assertThat(query.options("sex")).extracting(DictionaryOption::value).containsExactly("1","3");
            assertThat(query.options("sex")).extracting(DictionaryOption::label).containsExactly("Customer male","Unspecified");
            var snapshot=effective.dictionary("sex").orElseThrow();
            assertThat(snapshot.find("2").orElseThrow().label()).isEqualTo("Female");assertThat(snapshot.accepts("2")).isFalse();
            assertThat(snapshot.find("3").orElseThrow().origin()).isEqualTo(ConfigurationOrigin.TENANT_ONLY);
            assertThat(query.options("todo_state")).isEmpty();
            assertThat(effective.dictionary("todo_state").orElseThrow().find("1").orElseThrow().label()).isEqualTo("Not started");
            assertThat(legacy.types()).extracting(DictionaryService.TypeView::typeKey).containsExactly("customer_only");
            assertThat(query.options("customer_only")).extracting(DictionaryOption::value).containsExactly("x");
            assertThatThrownBy(()->legacy.items(fixture.sexTypeId)).isInstanceOf(BizException.class);return null;
        });
    }
    @Test void newTenantsUseSharedOptionsCacheByVersionAndWarmRegisteredDefinitions() {
        LocaleContextHolder.setLocale(Locale.US);
        Users.as(actor(7701,Set.of("config:dictionary:view","config:dictionary:update")),()->{
            assertThat(values(query.options("extend_dic_demo"))).containsExactly("v1","v2","v21","v22","v221","v23","v3","v31","v4");
            assertThat(query.options("sex")).extracting(DictionaryOption::label).containsExactly("Male","Female");
            long hits=cache.snapshot(ConfigCacheCatalog.DICTIONARIES).hits();
            query.options("sex");assertThat(cache.snapshot(ConfigCacheCatalog.DICTIONARIES).hits()).isEqualTo(hits+1);
            var state=layers.get("sex");
            layers.overrideItem("sex",state.snapshot().revision(),"2",new DefinedDictionaryService.ItemChange(null,null,true));
            assertThat(query.options("sex")).extracting(DictionaryOption::value).containsExactly("1");
            var before=layers.get("sex");
            new TransactionTemplate(transactions).execute(status->{
                layers.restoreItem("sex",before.snapshot().revision(),"2");
                assertThat(query.options("sex")).extracting(DictionaryOption::value).containsExactly("1","2");status.setRollbackOnly();return null;
            });
            assertThat(query.options("sex")).extracting(DictionaryOption::value).containsExactly("1");
            var page=warmup.enumerate("",16);assertThat(page.keys()).contains("sex|zh-CN","sex|en-US","extend_dic_demo|en-US");
            warmup.load("sex|zh-CN");
            Users.as(actor(7702,Set.of("config:dictionary:view")),()->{assertThat(query.options("sex")).extracting(DictionaryOption::value).containsExactly("1","2");return null;});
            return null;
        });
    }
    @TestConfiguration(proxyBeanMethods=false) static class FixtureConfiguration {
        @Bean LegacyFixture legacyDictionaryFixture(TenantDirectoryService directory,DictionaryTypeMapper types,DictionaryItemMapper items){return new LegacyFixture(directory,types,items);}
    }
    static class LegacyFixture implements ApplicationListener<ApplicationReadyEvent>,Ordered {
        private final TenantDirectoryService directory;private final DictionaryTypeMapper types;private final DictionaryItemMapper items;
        long tenant;long sexTypeId;
        LegacyFixture(TenantDirectoryService directory,DictionaryTypeMapper types,DictionaryItemMapper items){this.directory=directory;this.types=types;this.items=items;}
        @Override public int getOrder(){return -185;}
        @Override public void onApplicationEvent(ApplicationReadyEvent event) {
            var operator=new SmaPrincipal(991,"fixture-operator",1,"admin",null,false,DataAccess.unrestricted(),Set.of(),"UTC");
            tenant=Users.as(operator,()->directory.create(new TenantDirectoryService.TenantInput("dictionary-upgrade-fixture","Dictionary upgrade fixture")).id());
            TenantScope.within(tenant,()->{
                sexTypeId=type("sex",true,false);
                item(sexTypeId,"1","Customer male",null,true,0);item(sexTypeId,"2","女","config.dict.sex.i1",false,1);item(sexTypeId,"3","Unspecified",null,true,2);
                long todo=type("todo_state",false,false);item(todo,"1","未开始","config.dict.todo_state.i0",true,0);
                long own=type("customer_only",true,false);item(own,"x","Private x",null,true,0);return null;
            });
        }
        private long type(String key,boolean enabled,boolean tree){
            var row=new DictionaryTypeEntity();row.setTypeKey(key);row.setName(key);row.setTree(tree);row.setEnabled(enabled);row.setBuiltin(!key.equals("customer_only"));row.setSort(0);types.insert(row);return row.getId();
        }
        private void item(long type,String code,String name,String nameKey,boolean enabled,int sort){
            var row=new DictionaryItemEntity();row.setTypeId(type);row.setParentId(0L);row.setItemValue(code);row.setName(name);row.setNameKey(nameKey);row.setEnabled(enabled);row.setBuiltin(true);row.setSort(sort);items.insert(row);
        }
    }
}

