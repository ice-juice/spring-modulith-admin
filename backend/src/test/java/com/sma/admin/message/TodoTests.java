package com.sma.admin.message;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.message.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
class TodoTests {
 @Autowired TodoService todos;@Autowired UserService users;@Autowired MockMvcTester mvc;
 private final Instant due=Instant.parse("2026-11-11T08:00:00Z");private final PageQuery page=new PageQuery(1,20,null,null);
 private TodoService.Input input(int type,String content,String url,Long user){return new TodoService.Input("Task",content,url,type,2,due,user,
  new TodoService.Source("oa","message-1","workflow-1","https://example.test/workflow","sender-1","receiver-1"));}
 @Test void taskTypesUtcSourceFieldsStateMachineAndBatchRollback(){
  Users.as(Users.principal(101,241,DataAccess.own()),()->{
   var row=todos.save(null,input(4,"Details","/iam/users",null),false);
   assertThat(row.state()).isEqualTo(1);assertThat(todos.get(row.id()).dueAt()).isEqualTo(due);assertThat(todos.get(row.id()).source().requestId()).isEqualTo("workflow-1");
   assertThat(todos.pendingCount()).isEqualTo(1);todos.state(row.id(),2);todos.state(row.id(),5);todos.state(row.id(),2);
   assertThat(todos.read(row.id()).readAt()).isNotNull();assertThat(todos.read(row.id()).readAt()).isEqualTo(todos.get(row.id()).readAt());
   var other=todos.save(null,input(1,"Discarded","javascript:x()",null),false);assertThat(other.content()).isNull();assertThat(other.url()).isNull();
   assertThatThrownBy(()->todos.batchState(new LinkedHashSet<>(List.of(row.id(),999L)),3)).isInstanceOf(BizException.class);assertThat(todos.get(row.id()).state()).isEqualTo(2);
   var done=todos.state(row.id(),3);assertThat(done.completedAt()).isNotNull();assertThat(done.cancelledAt()).isNull();assertThat(todos.state(row.id(),3).completedAt()).isEqualTo(todos.get(row.id()).completedAt());
   assertThatThrownBy(()->todos.state(row.id(),2)).isInstanceOf(BizException.class);assertThatThrownBy(()->todos.save(row.id(),input(2,"Changed",null,null),false)).isInstanceOf(BizException.class);
   assertThat(todos.state(other.id(),4).cancelledAt()).isNotNull();assertThat(todos.pendingCount()).isZero();
   assertThat(todos.page(page,null,null,null,null,null,"dueAt",due,due.plusSeconds(1),false).total()).isEqualTo(2);
   assertThat(todos.page(page,null,null,null,null,null,"dueAt",null,due,false).total()).isZero();
   assertThatThrownBy(()->todos.page(page,null,null,null,null,null,"due_at;DROP",null,null,false)).isInstanceOf(BizException.class);
   return null;
  });
 }
 @Test void unsafeLinksMissingRequiredContentAndForeignOwnershipAreRejected(){
  Users.as(Users.principal(101,242,DataAccess.unrestricted()),()->{
   for(String url:List.of("javascript:x()","//example.test","/\\example.test","data:text/html,x","https://user:pass@example.test"))
    assertThatThrownBy(()->todos.save(null,input(3,null,url,null),false)).isInstanceOf(BizException.class);
   assertThatThrownBy(()->todos.save(null,input(2,"",null,null),false)).isInstanceOf(BizException.class);
   assertThatThrownBy(()->todos.save(null,input(1,null,null,202L),false)).isInstanceOf(BizException.class);
   var row=todos.save(null,input(2,"Details",null,null),false);
   Users.as(Users.principal(202,242,DataAccess.unrestricted()),()->{assertThatThrownBy(()->todos.get(row.id())).isInstanceOf(BizException.class);assertThatThrownBy(()->todos.state(row.id(),3)).isInstanceOf(BizException.class);return null;});
   Users.as(Users.principal(101,243,DataAccess.unrestricted()),()->{assertThatThrownBy(()->todos.get(row.id())).isInstanceOf(BizException.class);return null;});
   Users.as(new SmaPrincipal(101,"tester",242,"other",null,false,DataAccess.unrestricted(),Set.of(),"UTC"),()->{assertThatThrownBy(()->todos.get(row.id())).isInstanceOf(BizException.class);return null;});
   Users.as(Users.principal(101,242,DataAccess.unrestricted()),()->{assertThat(mvc.get().uri("/api/message/todos/managed")).hasStatus(403);return null;});
   return null;
  });
 }
 @Test void assignedTasksBelongToRecipientAndManagementDataScopeIsIndependent(){
  Users.as(Users.principal(101,244,DataAccess.unrestricted()),()->{
   var user=users.create(new UserService.Create(new UserService.Input("assignee","Assignee",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Q723f5a8e3"));
   var row=todos.save(null,input(3,null,"https://example.test/task",user.id()),true);
   assertThat(todos.pendingCount()).isZero();
   assertThat(todos.page(page,null,null,null,null,null,null,null,null,true).total()).isEqualTo(1);
   Users.as(Users.principal(user.id(),244,DataAccess.own()),()->{
    assertThat(todos.get(row.id()).title()).isEqualTo("Task");assertThat(todos.pendingCount()).isEqualTo(1);
    assertThat(todos.page(page,null,null,null,null,null,null,null,null,true).total()).isZero();todos.state(row.id(),3);return null;
   });return null;
  });
 }
 @Test void managedEndpointAppliesFiltersWithoutExpandingDataScope(){
  var manager=new SmaPrincipal(101,"manager",1244,"admin",null,false,DataAccess.unrestricted(),Set.of("message:todo:view"),"UTC");
  Users.as(manager,()->{
   var user=users.create(new UserService.Create(new UserService.Input("filter-assignee","Assignee",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Q723f5a8e3"));
   todos.save(null,new TodoService.Input("Needle",null,null,1,2,due,user.id(),null),true);
   todos.save(null,new TodoService.Input("Other","Details",null,2,1,due.plusSeconds(3600),user.id(),null),true);
   assertThat(mvc.get().uri("/api/message/todos/managed").param("keyword","Needle").param("state","1").param("type","1")
     .param("dateField","dueAt").param("from",due.toString()).param("to",due.plusSeconds(1).toString())).hasStatusOk()
     .bodyJson().hasPathSatisfying("$.data.total",v->v.assertThat().isEqualTo("1"));
   assertThat(mvc.get().uri("/api/message/todos/managed").param("keyword","Needle").param("state","3")).hasStatusOk()
     .bodyJson().hasPathSatisfying("$.data.total",v->v.assertThat().isEqualTo("0"));
   var restricted=new SmaPrincipal(user.id(),"assignee",1244,"admin",null,false,DataAccess.own(),Set.of("message:todo:view"),"UTC");
   Users.as(restricted,()->{assertThat(mvc.get().uri("/api/message/todos/managed").param("keyword","Needle")).hasStatusOk()
     .bodyJson().hasPathSatisfying("$.data.total",v->v.assertThat().isEqualTo("0"));return null;});
   return null;
  });
 }
}
