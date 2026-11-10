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
class RoleTests {
    @Autowired RoleService service;
    @Autowired DepartmentService departments;
    @Test
    void fiveScopesExpandAndCombineWithoutParentInheritance() {
        Users.as(Users.principal(101,121,DataAccess.unrestricted()), () -> {
            var root = departments.save(null,new DepartmentService.Input(0L,"HQ","hq",0,true));
            var child = departments.save(null,new DepartmentService.Input(root.id(),"Child","child",0,true));
            var all = service.save(null,input(0L,"all",DataScope.ALL,Set.of()));
            var self = service.save(null,input(all.id(),"self",DataScope.SELF,Set.of()));
            var dept = service.save(null,input(0L,"dept",DataScope.DEPARTMENT,Set.of()));
            var tree = service.save(null,input(0L,"tree",DataScope.DEPARTMENT_TREE,Set.of()));
            var custom = service.save(null,input(0L,"custom",DataScope.CUSTOM,Set.of(child.id())));
            assertThatThrownBy(() -> departments.delete(child.id())).isInstanceOf(BizException.class);
            assertThat(service.resolve(Set.of(self.id()),root.id())).isEqualTo(DataAccess.own());
            assertThat(service.resolve(Set.of(dept.id()),root.id()).departmentIds()).containsExactly(root.id());
            assertThat(service.resolve(Set.of(tree.id()),root.id()).departmentIds()).containsExactlyInAnyOrder(root.id(),child.id());
            assertThat(service.resolve(Set.of(custom.id(),self.id()),root.id())).isEqualTo(new DataAccess(false,Set.of(child.id()),true));
            assertThat(service.resolve(Set.of(all.id()),root.id()).all()).isTrue();
            assertThat(service.resolve(Set.of(),root.id())).isEqualTo(DataAccess.none());
            assertThatThrownBy(() -> service.save(all.id(), input(self.id(),"all",DataScope.ALL,Set.of()))).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.delete(all.id())).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.save(null,input(0L,"bad",DataScope.CUSTOM,Set.of()))).isInstanceOf(BizException.class);
            service.save(custom.id(),input(0L,"custom",DataScope.DEPARTMENT,Set.of()));
            assertThat(service.list().stream().filter(row -> row.id().equals(custom.id())).findFirst().orElseThrow().departmentIds()).isEmpty();
            return null;
        });
    }
    @Test
    void roleCatalogIsIsolatedByPortalAndDisabledRolesGrantNothing() {
        var admin = Users.principal(101,122,DataAccess.unrestricted());
        Users.as(admin, () -> {
            var role = service.save(null,input(0L,"operator",DataScope.ALL,Set.of()));
            var disabled = new RoleService.Input(0L,"operator","operator",0,false,DataScope.ALL,Set.of(),null);
            service.save(role.id(),disabled);
            assertThat(service.resolve(Set.of(role.id()),null)).isEqualTo(DataAccess.none());
            assertThat(service.options()).isEmpty();
            var other = new SmaPrincipal(101,"other",122,"customer",null,false,DataAccess.unrestricted(),Set.of(),"UTC");
            Users.as(other, () -> {
                assertThat(service.list()).isEmpty();
                assertThatThrownBy(() -> service.require(role.id())).isInstanceOf(BizException.class);
                service.save(null,input(0L,"operator",DataScope.SELF,Set.of()));
                return null;
            });
            return null;
        });
    }
    private RoleService.Input input(Long parent,String code,DataScope scope,Set<Long> custom) {
        return new RoleService.Input(parent,code,code,0,true,scope,custom,null);
    }
}
