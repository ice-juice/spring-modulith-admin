package com.sma.admin.auth;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.auth.internal.service.AuthPermissionCatalog;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.MenuView;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
class AccountSecurityMenuTests {
    private static final String KEY="auth:account-security:view";
    @Autowired PermissionService permissions;
    @Autowired RolePermissionService grants;
    @Autowired RoleService roles;

    @Test void superAdminGetsMenuByDefaultAndOrdinaryRolesNeedExplicitMenuGrant() {
        var actor=new SmaPrincipal(101,"menu-admin",1721,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
        Users.as(actor,()->{
            permissions.sync(new AuthPermissionCatalog().permissions());
            var entry=permissions.list().stream().filter(p->p.permissionKey().equals(KEY)).findFirst().orElseThrow();
            assertThat(entry.builtin()).isTrue();assertThat(entry.enabled()).isTrue();
            assertThat(entry.hidden()).isFalse();assertThat(entry.superAdminDefault()).isTrue();
            assertThat(grants.keys(Set.of(),true)).contains(KEY);
            var root=grants.menus(Set.of(),true).stream().filter(p->p.permission().equals("auth:security")).findFirst().orElseThrow();
            assertThat(root.children()).filteredOn(p->p.permission().equals(KEY)).singleElement()
                    .extracting(MenuView::path).isEqualTo("/auth/security");
            assertThat(grants.keys(Set.of(),false)).doesNotContain(KEY);
            assertThat(grants.menus(Set.of(),false)).isEmpty();
            var role=roles.save(null,new RoleService.Input(0L,"Security menu","securityMenu",0,true,DataScope.SELF,Set.of(),null));
            grants.replace(role.id(),Set.of(entry.id()));
            assertThat(grants.keys(Set.of(role.id()),false)).contains(KEY,"auth:security");
            assertThat(grants.menus(Set.of(role.id()),false).getFirst().children()).extracting(MenuView::path).contains("/auth/security");
            // A subsequent catalog sync must preserve the administrator's explicit switches and grant IDs.
            permissions.save(entry.id(),new PermissionService.Input(entry.parentId(),KEY,"My security",entry.nameKey(),"MENU",entry.path(),entry.icon(),"ROUTE",10,false,false,false,Map.of()));
            permissions.sync(new AuthPermissionCatalog().permissions());
            var kept=permissions.list().stream().filter(p->p.permissionKey().equals(KEY)).findFirst().orElseThrow();
            assertThat(kept.id()).isEqualTo(entry.id());assertThat(kept.enabled()).isFalse();assertThat(kept.superAdminDefault()).isFalse();
            assertThat(grants.selected(role.id())).contains(entry.id());
            assertThat(grants.keys(Set.of(),true)).doesNotContain(KEY);
            assertThat(grants.keys(Set.of(role.id()),false)).doesNotContain(KEY);
            Users.as(new SmaPrincipal(102,"other",1722,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC"),()->{
                assertThat(grants.keys(Set.of(),true)).doesNotContain(KEY);return null;
            });
            return null;
        });
    }
}
