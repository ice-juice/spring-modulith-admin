package com.sma.admin.iam;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.*;
@IntegrationTest
class NavigationTests {
    @Autowired NavigationService service;
    @Autowired PermissionService permissions;
    @Autowired IamPermissionCatalog catalog;
    @Test
    void onlyTopLevelMenusCanBeAssignedAndVisibleMenusAreIntersected() {
        Users.as(Users.principal(101,161,DataAccess.unrestricted()),() -> {
            permissions.sync(catalog.permissions());
            var root=permissions.list().stream().filter(row -> row.permissionKey().equals("iam:organization")).findFirst().orElseThrow();
            var child=permissions.list().stream().filter(row -> row.permissionKey().equals("iam:department:view")).findFirst().orElseThrow();
            assertThatThrownBy(() -> service.save(null,new NavigationService.Input("Bad",null,0,true,Set.of(child.id()),Map.of()))).isInstanceOf(BizException.class);
            var nav=service.save(null,new NavigationService.Input("系统",null,0,true,Set.of(root.id()),Map.of("en-US","System")));
            assertThat(service.visible(Set.of())).isEmpty();
            assertThat(service.visible(Set.of(root.id()))).hasSize(1);
            Users.as(Users.principal(202,162,DataAccess.unrestricted()),() -> { assertThat(service.list()).isEmpty(); return null; });
            service.delete(nav.id()); assertThat(service.list()).isEmpty(); return null;
        });
    }
}
