package com.sma.admin.iam;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
class PermissionTests {
    @Autowired PermissionService service;
    @Autowired PermissionCatalogSync sync;
    @Test
    void startupCatalogIsIdempotentAndBuiltinKeysAreProtected() {
        int before=service.list().size(); sync.synchronize(); assertThat(service.list()).hasSize(before);
        var menu=service.list().stream().filter(row -> row.permissionKey().equals("iam:department:view")).findFirst().orElseThrow();
        assertThat(menu.builtin()).isTrue();
        assertThatThrownBy(() -> service.delete(menu.id())).isInstanceOf(BizException.class);
        assertThat(menu.type()).isEqualTo("MENU");
    }
    @Test
    void customNamesTranslateWithFallbackWhileTreesAndRoutesAreValidated() {
        Users.as(Users.principal(101,131,DataAccess.unrestricted()), () -> {
            var row=service.save(null,input(0L,"iam:custom:view","自定义","/custom",Map.of("en-US","Custom menu")));
            try {
                LocaleContextHolder.setLocale(java.util.Locale.US);
                assertThat(service.list()).extracting(PermissionService.View::name).containsExactly("Custom menu");
                LocaleContextHolder.setLocale(java.util.Locale.CHINA);
                assertThat(service.list()).extracting(PermissionService.View::name).containsExactly("自定义");
            } finally { LocaleContextHolder.resetLocaleContext(); }
            assertThatThrownBy(() -> service.save(row.id(),input(row.id(),"iam:custom:view","x","/custom",Map.of()))).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.save(null,input(0L,"iam:bad:view","x","javascript:alert(1)",Map.of()))).isInstanceOf(BizException.class);
            assertThat(service.tree()).hasSize(1);
            service.delete(row.id()); assertThat(service.list()).isEmpty();
            return null;
        });
    }
    @Test
    void promotesOnlyUncustomizedDiscoveredActionsAndPreservesIdsAndAdministratorSettings() {
        Users.as(Users.principal(101,1131,DataAccess.unrestricted()), () -> {
            String grant="iam:role:grant", reset="iam:user:reset-password";
            service.sync(java.util.List.of(
                new PermissionSpec(grant,null,null,grant,"LOGIC",null,null,100,"admin",true),
                new PermissionSpec(reset,null,null,reset,"LOGIC",null,null,100,"admin",true)));
            var original=service.list().stream().filter(row->row.permissionKey().equals(grant)).findFirst().orElseThrow();
            service.save(original.id(),new PermissionService.Input(0L,grant,grant,null,"LOGIC",null,null,"ROUTE",37,false,true,false,Map.of("en-US","Custom grant label")));
            var custom=service.list().stream().filter(row->row.permissionKey().equals(reset)).findFirst().orElseThrow();
            service.save(custom.id(),new PermissionService.Input(0L,reset,"Custom password action",null,"LOGIC",null,null,"ROUTE",99,true,false,true,Map.of()));
            service.sync(new IamPermissionCatalog().permissions());
            var promoted=service.list().stream().filter(row->row.permissionKey().equals(grant)).findFirst().orElseThrow();
            assertThat(promoted.id()).isEqualTo(original.id());
            assertThat(promoted.type()).isEqualTo("BUTTON");
            assertThat(promoted.parentId()).isNotZero();
            assertThat(promoted.nameKey()).isEqualTo("iam.action.grant");
            assertThat(promoted.sort()).isEqualTo(37);
            assertThat(promoted.enabled()).isFalse();
            assertThat(promoted.hidden()).isTrue();
            assertThat(promoted.superAdminDefault()).isFalse();
            assertThat(promoted.translations()).containsEntry("en-US","Custom grant label");
            var kept=service.list().stream().filter(row->row.id().equals(custom.id())).findFirst().orElseThrow();
            assertThat(kept.defaultName()).isEqualTo("Custom password action");
            assertThat(kept.type()).isEqualTo("LOGIC");
            assertThat(kept.parentId()).isZero();
            service.sync(new IamPermissionCatalog().permissions());
            assertThat(service.list()).hasSize(new IamPermissionCatalog().permissions().size());
            return null;
        });
    }
    private PermissionService.Input input(Long parent,String key,String name,String path,Map<String,String> translations) {
        return new PermissionService.Input(parent,key,name,null,"MENU",path,null,"ROUTE",0,true,false,true,translations);
    }
}
