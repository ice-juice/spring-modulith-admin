package com.sma.admin.rule;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.rule.internal.service.RulePermissionCatalog;
import com.sma.admin.support.*;

@IntegrationTest
class RuleMenuTests {
    @Autowired PermissionService permissions;
    @Autowired RolePermissionService grants;
    @Autowired RoleService roles;
    @Autowired NavigationService navigation;
    @Autowired RulePermissionCatalog catalog;

    @Test void groupsOldMenusAndButtonsWithoutChangingLeafGrantsOrAdminSettings() {
        var actor=new SmaPrincipal(12001,"rule-menu",19101,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
        Users.as(actor,()->{
            var legacy=catalog.permissions().stream().filter(spec->!spec.key().equals("rule:center"))
                    .map(spec->new PermissionSpec(spec.key(),null,spec.nameKey(),spec.defaultName(),spec.type(),spec.path(),spec.icon(),spec.sort(),spec.portal(),spec.superAdminDefault())).toList();
            permissions.sync(legacy);
            var old=permissions.list();
            var menu=old.stream().filter(row->row.permissionKey().equals("rule:catalog:view")).findFirst().orElseThrow();
            var button=old.stream().filter(row->row.permissionKey().equals("rule:instance:edit")).findFirst().orElseThrow();
            var disabled=old.stream().filter(row->row.permissionKey().equals("rule:template:lifecycle")).findFirst().orElseThrow();
            permissions.save(disabled.id(),new PermissionService.Input(0L,disabled.permissionKey(),"Custom lifecycle",disabled.nameKey(),"BUTTON",null,null,"ROUTE",37,false,true,false,Map.of("en-US","Custom lifecycle")));
            var role=roles.save(null,new RoleService.Input(0L,"Rule operator","rule-operator",0,true,DataScope.SELF,Set.of(),null));
            grants.replace(role.id(),Set.of(menu.id(),button.id()));
            var group=navigation.save(null,new NavigationService.Input("Rules navigation",null,0,true,Set.of(menu.id()),Map.of()));
            var oldKeys=grants.keys(Set.of(role.id()),false);
            permissions.sync(catalog.permissions()); permissions.sync(catalog.permissions());
            var current=permissions.list();
            var root=current.stream().filter(row->row.permissionKey().equals("rule:center")).findFirst().orElseThrow();
            assertThat(current).hasSize(23);
            assertThat(current).filteredOn(row->!row.permissionKey().equals("rule:center")).allMatch(row->row.parentId().equals(root.id()));
            assertThat(current).filteredOn(row->row.type().equals("MENU")).hasSize(5);
            assertThat(navigation.list().stream().filter(row->row.id().equals(group.id())).findFirst().orElseThrow().menuIds())
                    .containsExactly(root.id());
            for(var row:old) assertThat(current).anyMatch(next->next.id().equals(row.id()) && next.permissionKey().equals(row.permissionKey()));
            assertThat(grants.keys(Set.of(role.id()),false)).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.Stream.concat(oldKeys.stream(),java.util.stream.Stream.of("rule:center")).toList());
            assertThat(grants.menus(Set.of(role.id()),false)).singleElement().satisfies(center->{
                assertThat(center.permission()).isEqualTo("rule:center");
                assertThat(center.children()).singleElement().satisfies(child->assertThat(child.path()).isEqualTo("/rule/catalog"));
            });
            var kept=current.stream().filter(row->row.id().equals(disabled.id())).findFirst().orElseThrow();
            assertThat(kept.enabled()).isFalse();assertThat(kept.hidden()).isTrue();assertThat(kept.superAdminDefault()).isFalse();
            assertThat(kept.sort()).isEqualTo(37);assertThat(kept.translations()).containsEntry("en-US","Custom lifecycle");
            assertThat(grants.keys(Set.of(),false)).isEmpty();
            return null;
        });
    }

    @Test void promotesOldPathlessViewButtonsAndKeepsCustomParents() {
        var actor=new SmaPrincipal(12002,"rule-menu",19102,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
        Users.as(actor,()->{
            var legacy=catalog.permissions().stream().filter(spec->!spec.key().equals("rule:center"))
                    .map(spec->new PermissionSpec(spec.key(),null,spec.nameKey(),spec.defaultName(),"BUTTON",null,null,0,spec.portal(),true)).toList();
            permissions.sync(legacy);
            var before=permissions.list();
            var custom=permissions.save(null,new PermissionService.Input(0L,"rule:custom","Custom rules",null,"DIRECTORY",null,null,"ROUTE",0,true,false,true,Map.of()));
            var edit=before.stream().filter(row->row.permissionKey().equals("rule:scene:edit")).findFirst().orElseThrow();
            permissions.save(edit.id(),new PermissionService.Input(custom.id(),edit.permissionKey(),edit.defaultName(),edit.nameKey(),edit.type(),null,null,"ROUTE",0,true,false,true,Map.of()));
            permissions.sync(catalog.permissions());
            var current=permissions.list();
            assertThat(current).filteredOn(row->row.type().equals("MENU")).hasSize(5);
            assertThat(current.stream().filter(row->row.id().equals(edit.id())).findFirst().orElseThrow().parentId()).isEqualTo(custom.id());
            for(var row:before) assertThat(current).anyMatch(next->next.id().equals(row.id()));
            return null;
        });
    }
}
