package com.sma.admin.iam;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
class PermissionMenuGroupingTests {
    @Autowired PermissionService permissions;
    @Autowired RolePermissionService grants;
    @Autowired RoleService roles;
    @Autowired NavigationService navigation;

    private List<PermissionSpec> grouped() {
        return List.of(
            new PermissionSpec("extension:management",null,"extension.menu.management","业务扩展","DIRECTORY",null,"Package",85,"admin",true),
            new PermissionSpec("extension:console:view","extension:management","extension.menu.console","扩展管理","MENU","/extension/manage",null,0,"admin",false));
    }
    private PermissionService.View menu(String key) {
        return permissions.list().stream().filter(row -> row.permissionKey().equals(key)).findFirst().orElseThrow();
    }
    @Test void upgradePreservesLeafGrantsOverridesAndNavigationAndIsIdempotent() {
        Users.as(new SmaPrincipal(101,"super",1141,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC"), () -> {
            permissions.sync(List.of(new PermissionSpec("extension:console:view",null,"extension.menu.console","业务扩展","MENU","/extension/manage","Package",85,"admin",false)));
            var leaf=menu("extension:console:view");
            var role=roles.save(null,new RoleService.Input(0L,"Publisher","publisher",0,true,DataScope.SELF,Set.of(),null));
            var ungranted=roles.save(null,new RoleService.Input(0L,"Reader","reader",0,true,DataScope.SELF,Set.of(),null));
            grants.replace(role.id(),Set.of(leaf.id()));
            var nav=navigation.save(null,new NavigationService.Input("Operations",null,0,true,Set.of(leaf.id()),Map.of()));
            permissions.save(leaf.id(),new PermissionService.Input(0L,leaf.permissionKey(),"Custom console",leaf.nameKey(),"MENU",leaf.path(),"Package","ROUTE",37,true,false,false,Map.of("en-US","Custom console")));
            permissions.sync(grouped()); permissions.sync(grouped());
            var parent=menu("extension:management"); var current=menu("extension:console:view");
            assertThat(current.id()).isEqualTo(leaf.id());
            assertThat(current.parentId()).isEqualTo(parent.id());
            assertThat(current.defaultName()).isEqualTo("Custom console");
            assertThat(current.translations()).containsEntry("en-US","Custom console");
            assertThat(current.sort()).isEqualTo(37);
            assertThat(current.superAdminDefault()).isFalse();
            assertThat(grants.selected(role.id())).containsExactlyInAnyOrder(leaf.id(),parent.id());
            assertThat(grants.selected(ungranted.id())).isEmpty();
            assertThat(grants.keys(Set.of(),true)).doesNotContain(leaf.permissionKey());
            assertThat(grants.menus(Set.of(),true)).isEmpty();
            var tree=grants.menus(Set.of(role.id()),false);
            assertThat(tree).hasSize(1);
            assertThat(tree.getFirst().children()).extracting(com.sma.admin.iam.MenuView::id).containsExactly(leaf.id());
            assertThat(navigation.list().stream().filter(row->row.id().equals(nav.id())).findFirst().orElseThrow().menuIds()).containsExactly(parent.id());
            permissions.save(leaf.id(),new PermissionService.Input(parent.id(),leaf.permissionKey(),"Custom console",leaf.nameKey(),"MENU",leaf.path(),null,"ROUTE",37,false,true,false,Map.of()));
            permissions.sync(grouped());
            assertThat(menu(leaf.permissionKey()).enabled()).isFalse();
            assertThat(menu(leaf.permissionKey()).hidden()).isTrue();
            assertThat(grants.keys(Set.of(role.id()),false)).doesNotContain(leaf.permissionKey());
            return null;
        });
    }
    @Test void freshInstallIsGroupedAndAdministratorPlacementIsNotOverwritten() {
        Users.as(Users.principal(101,1142,DataAccess.unrestricted()), () -> {
            permissions.sync(grouped());
            var leaf=menu("extension:console:view");
            assertThat(permissions.tree()).hasSize(1);
            assertThat(permissions.tree().getFirst().children()).extracting(PermissionService.View::id).containsExactly(leaf.id());
            var custom=permissions.save(null,new PermissionService.Input(0L,"iam:custom-group","Custom",null,"DIRECTORY",null,null,"ROUTE",1,true,false,true,Map.of()));
            permissions.save(leaf.id(),new PermissionService.Input(custom.id(),leaf.permissionKey(),leaf.defaultName(),leaf.nameKey(),"MENU",leaf.path(),null,"ROUTE",0,true,false,false,Map.of()));
            permissions.sync(grouped());
            assertThat(menu(leaf.permissionKey()).parentId()).isEqualTo(custom.id());
            return null;
        });
    }
}
