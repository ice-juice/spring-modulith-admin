package com.sma.admin.workspace;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.message.internal.service.TodoService;
import com.sma.admin.workspace.internal.service.DashboardService;
import com.sma.admin.support.*;
@IntegrationTest
class DashboardTests {
 @Autowired DashboardService dashboard;@Autowired UserService users;@Autowired TodoService todos;
 private UserService.View user(String name){return users.create(new UserService.Create(new UserService.Input(name,name,null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Dashboard@Secure2026"));}
 @Test void moduleContributionsRespectPermissionTenantPortalAndPersonalDataScope(){
  Users.as(Users.principal(101,295,DataAccess.unrestricted()),()->{
   var a=user("dashboard-a");var b=user("dashboard-b");
   todos.save(null,new TodoService.Input("Other private task",null,null,1,1,Instant.now().plusSeconds(3600),b.id(),null),true);
   var actor=new SmaPrincipal(a.id(),"dashboard-a",295,"admin",null,false,DataAccess.own(),Set.of("iam:user:view"),"UTC");
   Users.as(actor,()->{
    todos.save(null,new TodoService.Input("My task",null,null,1,1,Instant.now().plusSeconds(3600),null,null),false);
    var view=dashboard.get();assertThat(view.metrics()).anySatisfy(row->{assertThat(row.id()).isEqualTo("iam.users");assertThat(row.value()).isEqualTo(1);});
    assertThat(view.metrics()).noneMatch(row->row.id().equals("iam.roles")||row.id().equals("file.assets"));
    assertThat(view.entries()).extracting(DashboardService.Entry::title).contains("My task").doesNotContain("Other private task");
    assertThat(view.entries()).allSatisfy(row->assertThat(row.path()).startsWith("/message/inbox?tab=tasks&focus="));return null;
   });
   Users.as(new SmaPrincipal(a.id(),"dashboard-a",296,"admin",null,false,DataAccess.own(),Set.of("iam:user:view"),"UTC"),()->{
    assertThat(dashboard.get().metrics()).allSatisfy(row->assertThat(row.value()).isZero());assertThat(dashboard.get().entries()).isEmpty();return null;
   });
   Users.as(new SmaPrincipal(a.id(),"dashboard-a",295,"other",null,false,DataAccess.own(),Set.of("iam:user:view"),"UTC"),()->{
    assertThat(dashboard.get().metrics()).allSatisfy(row->assertThat(row.value()).isZero());assertThat(dashboard.get().entries()).isEmpty();return null;
   });return null;
  });
 }
}
