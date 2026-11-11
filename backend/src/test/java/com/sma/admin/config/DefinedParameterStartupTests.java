package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestPropertySource;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.service.TenantDirectoryService;

/** Places representative old rows before the actual host migration listener, then exercises the unchanged Java query API. */
@IntegrationTest @Import(DefinedParameterStartupTests.FixtureConfiguration.class)
@TestPropertySource(properties="sma.tenant.platform-admin-user-ids=991")
class DefinedParameterStartupTests {
    @Autowired LegacyFixture fixture;
    @Autowired ParameterQuery query;
    @Autowired ParameterService legacy;
    @Autowired DefinedParameterService layers;
    @Autowired ParameterMapper rows;
    @Autowired ConfigNames names;
    @Autowired BuiltinParameters builtin;

    @Test void actualStartupBindsExistingValuesAndPreservesPrivateSecretsAndPresentation() {
        TenantScope.within(fixture.tenant,()->{
            assertThat(query.stringValue("ui.theme","fallback")).isEqualTo("dark");
            var theme=layers.get("ui.theme");assertThat(theme.rowId()).isEqualTo(fixture.themeId);
            assertThat(theme.snapshot().origin()).isEqualTo(ConfigurationOrigin.TENANT_OVERRIDE);
            assertThat(layers.get("ui.tabsEnabled").snapshot().origin()).isEqualTo(ConfigurationOrigin.TENANT_OVERRIDE);
            assertThat(rows.selectById(fixture.themeId).getSort()).isEqualTo(42);
            assertThat(names.translations("parameter",fixture.themeId)).containsEntry("en-US","Kept customer title");
            assertThat(query.stringValue("customer.privateCredential","missing")).isEqualTo("old-private-credential");
            assertThat(query.publicValues()).doesNotContainKey("customer.privateCredential").doesNotContainKey("auth.sessionTtlSeconds");
            assertThat(legacy.list(null,null)).filteredOn(value->value.paramKey().equals("customer.privateCredential"))
                    .singleElement().satisfies(value->{assertThat(value.secret()).isTrue();assertThat(value.value()).isNull();});
            assertThatThrownBy(()->legacy.delete(fixture.themeId)).isInstanceOf(com.sma.admin.common.BizException.class);
            return null;
        });
    }
    @Test void newTenantsUseSharedDefaultsAndOrdinaryInitializationCannotMakeCopies() {
        TenantScope.within(7341,()->{
            builtin.initialize();builtin.initialize();
            assertThat(rows.selectCount(null)).isZero();
            assertThat(query.booleanValue("system.departmentEnabled",false)).isTrue();
            assertThat(query.longValue("auth.sessionTtlSeconds",10)).isEqualTo(28800);
            assertThat(query.stringValue("UI.THEME","fallback")).isEqualTo("light");
            assertThat(layers.get("ui.theme").snapshot().origin()).isEqualTo(ConfigurationOrigin.PLATFORM);
            assertThat(query.stringValue("unknown.key","caller-default")).isEqualTo("caller-default");return null;
        });
    }
    @TestConfiguration(proxyBeanMethods=false) static class FixtureConfiguration {
        @Bean LegacyFixture legacyParameterFixture(TenantDirectoryService directory,ParameterMapper rows,ConfigNames names) {
            return new LegacyFixture(directory,rows,names);
        }
    }
    static class LegacyFixture implements ApplicationListener<ApplicationReadyEvent>,Ordered {
        private final TenantDirectoryService directory;private final ParameterMapper rows;private final ConfigNames names;
        long tenant;long themeId;
        LegacyFixture(TenantDirectoryService directory,ParameterMapper rows,ConfigNames names) {this.directory=directory;this.rows=rows;this.names=names;}
        @Override public int getOrder(){return -185;}
        @Override public void onApplicationEvent(ApplicationReadyEvent event) {
            var operator=new SmaPrincipal(991,"fixture-operator",1,"admin",null,false,DataAccess.unrestricted(),Set.of("tenant:tenant:create"),"UTC");
            tenant=Users.as(operator,()->directory.create(new TenantDirectoryService.TenantInput("parameter-upgrade-fixture","Parameter upgrade fixture")).id());
            // PROVISIONING tenants are migrated as well; acceptsBusiness is not a migration filter.
            TenantScope.within(tenant,()->{
                themeId=seed("ui.theme","STRING","dark",false);
                seed("ui.tabsEnabled","BOOLEAN","true",false);
                seed("customer.privateCredential","STRING","old-private-credential",true);
                names.replace("parameter",themeId,Map.of("en-US","Kept customer title"));return null;
            });
        }
        private long seed(String key,String type,String value,boolean secret) {
            var row=new ParameterEntity();row.setParamKey(key);row.setGroupKey("appearance");row.setName("Customer title");row.setValueType(type);
            row.setParamValue(value);row.setSecret(secret);row.setPublicReadable(false);row.setBuiltin(!secret);row.setSort(42);row.setBrandArchived(false);rows.insert(row);return row.getId();
        }
    }
}
