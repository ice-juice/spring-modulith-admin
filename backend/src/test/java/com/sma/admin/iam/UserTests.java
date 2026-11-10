package com.sma.admin.iam;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
class UserTests {
    @Autowired UserService users;
    @Autowired RoleService roles;
    @Autowired PostService posts;
    @Autowired DepartmentService departments;
    @Autowired MockMvcTester mvc;
    @Test
    void accountRelationsPasswordResetReferencesAndTenantIsolation() {
        var admin=new SmaPrincipal(101,"super",151,"admin",null,true,DataAccess.unrestricted(),Set.of("iam:user:view"),"UTC");
        Users.as(admin,() -> {
            var dept=departments.save(null,new DepartmentService.Input(0L,"Sales","sales",0,true));
            var role=roles.save(null,new RoleService.Input(0L,"Operator","operator",0,true,DataScope.SELF,Set.of(),null));
            var post=posts.save(null,new PostService.Input("Manager","mgr",0,true,null));
            var input=input("alice",dept.id(),Set.of(role.id()),Set.of(post.id()));
            var row=users.create(new UserService.Create(input,"Alice@Secure2026"));
            assertThat(users.authenticate("alice","wrong","admin")).isEmpty();
            assertThat(users.authenticate("alice","Alice@Secure2026","admin")).get().extracting(AccountView::id).isEqualTo(row.id());
            assertThat(row.roleIds()).containsExactly(role.id());
            assertThat(users.page(new PageQuery(1,20,null,null),null,null,null,role.id(),post.id()).items()).extracting(UserService.View::id).containsExactly(row.id());
            assertThatThrownBy(() -> departments.delete(dept.id())).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> roles.delete(role.id())).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> posts.delete(post.id())).isInstanceOf(BizException.class);
            Users.as(admin,() -> {
                assertThat(mvc.get().uri("/api/iam/users/"+row.id())).hasStatusOk().bodyJson().doesNotHavePath("$.data.passwordHash");
                return null;
            });
            users.resetPassword(row.id(),"Alice@Changed2026");
            assertThat(users.authenticate("alice","Alice@Secure2026","admin")).isEmpty();
            assertThat(users.authenticate("alice","Alice@Changed2026","admin")).isPresent();
            Users.as(Users.principal(202,152,DataAccess.unrestricted()),() -> { assertThatThrownBy(() -> users.get(row.id())).isInstanceOf(BizException.class); return null; });
            users.delete(row.id()); roles.delete(role.id()); posts.delete(post.id()); departments.delete(dept.id());
            return null;
        });
    }
    @Test
    void restrictedAdministratorCannotAssignUsersOutsideTheirDataScope() {
        Users.as(Users.principal(101,154,DataAccess.own()),() -> {
            assertThatThrownBy(() -> users.create(new UserService.Create(input("limited",null,Set.of(),Set.of()),"Limited@Secure2026")))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> roles.save(null,new RoleService.Input(0L,"All","all",0,true,DataScope.ALL,Set.of(),null)))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            return null;
        });
    }
    @Test
    void selfAndSuperAdminProtectionAndOwnPasswordChanges() {
        Users.as(Users.principal(101,153,DataAccess.unrestricted()),() -> {
            users.bootstrap("Admin@Secure2026");
            var admin=users.authenticate("admin","Admin@Secure2026","admin").orElseThrow();
            var principal=new SmaPrincipal(admin.id(),"admin",153,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
            Users.as(principal,() -> {
                assertThatThrownBy(() -> users.delete(admin.id())).isInstanceOf(BizException.class);
                assertThatThrownBy(() -> users.resetPassword(admin.id(),"Changed@Secure2026")).isInstanceOf(BizException.class);
                users.changeOwnPassword("Admin@Secure2026","Changed@Secure2026");
                assertThat(users.authenticate("admin","Changed@Secure2026","admin")).isPresent();
                return null;
            });
            return null;
        });
    }
    private UserService.Input input(String username,Long dept,Set<Long> roles,Set<Long> posts) {
        return new UserService.Input(username,"Alice",dept,"alice@example.test",null,"UNSPECIFIED",true,roles,posts,"en-US","America/New_York");
    }
}
