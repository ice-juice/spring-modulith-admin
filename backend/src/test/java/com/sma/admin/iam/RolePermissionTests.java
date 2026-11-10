package com.sma.admin.iam;
import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
class RolePermissionTests {
    @Autowired RolePermissionService service;
    @Autowired RoleService roles;
    @Autowired PermissionService permissions;
    @Autowired IamPermissionCatalog catalog;
    @Test
    void grantsAreAtomicTenantSafeAndIncludeNavigationAncestors() {
        var admin=new SmaPrincipal(101,"super",141,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
        Users.as(admin,() -> {
            permissions.sync(catalog.permissions());
            var role=roles.save(null,new RoleService.Input(0L,"Operator","operator",0,true,DataScope.SELF,Set.of(),null));
            var create=permissions.list().stream().filter(row -> row.permissionKey().equals("iam:department:create")).findFirst().orElseThrow();
            service.replace(role.id(),Set.of(create.id()));
            assertThat(service.selected(role.id())).hasSize(3);
            assertThat(service.keys(Set.of(role.id()),false)).containsExactlyInAnyOrder("iam:department:create","iam:department:view","iam:organization");
            assertThat(service.menus(Set.of(role.id()),false)).hasSize(1);
            assertThatThrownBy(() -> service.replace(role.id(),Set.of(-1L))).isInstanceOf(BizException.class);
            assertThat(service.selected(role.id())).hasSize(3);
            var limited=Users.principal(102,141,DataAccess.own());
            Users.as(limited,() -> { assertThatThrownBy(() -> service.replace(role.id(),Set.of(create.id()))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class); return null; });
            service.replace(role.id(),Set.of());
            assertThat(service.keys(Set.of(role.id()),false)).isEmpty();
            assertThat(service.keys(Set.of(),true)).contains("iam:department:create");
            return null;
        });
    }
}
