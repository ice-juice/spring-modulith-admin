package com.sma.admin.message;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.service.FileService;
import com.sma.admin.iam.AudienceQuery;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.message.internal.service.NoticeService;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
class NoticeTests {
 @Autowired NoticeService notices;@Autowired UserService users;@Autowired RoleService roles;@Autowired PostService posts;
 @Autowired DepartmentService departments;@Autowired AudienceQuery audience;@Autowired FileService files;@Autowired MockMvcTester mvc;
 private final PageQuery page=new PageQuery(1,20,null,null);
 private UserService.View user(String name,Long dept,Set<Long> roleIds,Set<Long> postIds){return users.create(new UserService.Create(
  new UserService.Input(name,name,dept,null,null,"UNSPECIFIED",true,roleIds,postIds,null,null),"Notice@Secure2026"));}
 private NoticeService.Input input(String kind,Set<Long> ids,Set<Long> fileIds){return new NoticeService.Input("Notice","<p>Hello <b>world</b></p>","1",2,kind,ids,fileIds);}
 @Test void fiveAudienceTypesValidateMembershipPortalTenantAndDataScope(){
  Users.as(Users.principal(101,231,DataAccess.unrestricted()),()->{
   var dept=departments.save(null,new DepartmentService.Input(0L,"Team","team",0,true));
   var role=roles.save(null,new RoleService.Input(0L,"Reader","reader",0,true,DataScope.SELF,Set.of(),null));
   var post=posts.save(null,new PostService.Input("Engineer","engineer",0,true,null));
   var a=user("audience-a",dept.id(),Set.of(role.id()),Set.of(post.id()));var b=user("audience-b",null,Set.of(),Set.of());
   assertThat(audience.resolve("ALL",Set.of()).keySet()).containsExactlyInAnyOrder(a.id(),b.id());
   for(String type:List.of("ROLE","DEPARTMENT","POST","USER")){
    long id=switch(type){case "ROLE"->role.id();case "DEPARTMENT"->dept.id();case "POST"->post.id();default->a.id();};
    assertThat(audience.resolve(type,Set.of(id)).keySet()).containsExactly(a.id());
   }
   assertThatThrownBy(()->audience.resolve("USER",Set.of(999L))).isInstanceOf(BizException.class);
   assertThatThrownBy(()->audience.resolve("ROLE",Set.of(999L))).isInstanceOf(BizException.class);
   Users.as(new SmaPrincipal(101,"operator",231,"other",null,false,DataAccess.unrestricted(),Set.of(),"UTC"),()->{
    assertThat(audience.resolve("ALL",Set.of())).isEmpty();assertThatThrownBy(()->audience.resolve("USER",Set.of(a.id()))).isInstanceOf(BizException.class);return null;
   });
   Users.as(Users.principal(a.id(),231,DataAccess.own()),()->{assertThat(audience.resolve("ALL",Set.of()).keySet()).containsExactly(a.id());return null;});
   Users.as(Users.principal(101,232,DataAccess.unrestricted()),()->{assertThatThrownBy(()->audience.resolve("USER",Set.of(a.id()))).isInstanceOf(BizException.class);return null;});return null;
  });
 }
 @Test void readersAreIdempotentAndInboxAttachmentsFollowRecipientSnapshotAndRecycle(){
  Users.as(Users.principal(101,233,DataAccess.unrestricted()),()->{
   var a=user("notice-a",null,Set.of(),Set.of());var b=user("notice-b",null,Set.of(),Set.of());
   var file=files.upload(new MockMultipartFile("file","notice.txt","text/plain",new byte[]{1,2,3}));
   var row=notices.save(null,input("USER",Set.of(a.id()),Set.of(file.id())));
   assertThat(row.recipientCount()).isEqualTo(1);assertThat(row.readCount()).isZero();
   java.time.Instant[] first={null};
   Users.as(Users.principal(a.id(),233,DataAccess.own()),()->{
    assertThat(notices.inbox(page,true).total()).isEqualTo(1);first[0]=notices.read(row.id()).readAt();
    assertThat(first[0]).isNotNull();assertThat(notices.read(row.id()).readAt()).isEqualTo(first[0]);
    assertThat(notices.unreadCount()).isZero();assertThat(files.metadata(file.id()).id()).isEqualTo(file.id());
    assertThatThrownBy(()->notices.get(row.id())).isInstanceOf(BizException.class);return null;
   });
   Users.as(Users.principal(b.id(),233,DataAccess.own()),()->{
    assertThat(notices.inbox(page,false).total()).isZero();assertThatThrownBy(()->notices.read(row.id())).isInstanceOf(BizException.class);
    assertThatThrownBy(()->files.metadata(file.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;
   });
   assertThatThrownBy(()->files.delete(file.id())).isInstanceOf(BizException.class);
   notices.save(row.id(),input("USER",Set.of(a.id(),b.id()),Set.of(file.id())));
   assertThat(notices.get(row.id()).readCount()).isEqualTo(1);
   assertThat(notices.readers(row.id(),page).items()).anySatisfy(reader->{assertThat(reader.userId()).isEqualTo(a.id());assertThat(reader.readAt()).isEqualTo(first[0]);});
   notices.delete(row.id());assertThat(notices.page(page,null,null,null,null,true).total()).isEqualTo(1);
   Users.as(Users.principal(a.id(),233,DataAccess.own()),()->{
    assertThat(notices.inbox(page,false).total()).isZero();assertThatThrownBy(()->files.metadata(file.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;
   });
   assertThatThrownBy(()->files.delete(file.id())).isInstanceOf(BizException.class);
   notices.restore(row.id());assertThat(notices.get(row.id()).readCount()).isEqualTo(1);
   notices.save(row.id(),input("USER",Set.of(b.id()),Set.of()));files.delete(file.id());
   Users.as(Users.principal(a.id(),233,DataAccess.own()),()->{assertThat(notices.inbox(page,false).total()).isZero();return null;});return null;
  });
 }
 @Test void contentIsSanitizedAndForeignAssetsCannotBeAttachedAndHttpIsPermissionProtected(){
  Users.as(Users.principal(101,234,DataAccess.unrestricted()),()->{
   var a=user("safe-content",null,Set.of(),Set.of());
   var row=notices.save(null,new NoticeService.Input("Safe","<p onclick='x()'>Text<script>alert(1)</script><a href='javascript:x()'>bad</a><iframe src='https://example.test'></iframe><a href='https://example.test'>safe</a></p>","2",3,"USER",Set.of(a.id()),Set.of()));
   assertThat(row.content()).doesNotContain("onclick","script","iframe").contains("https://example.test","noopener noreferrer");
   var other=Users.as(Users.principal(102,234,DataAccess.unrestricted()),()->files.upload(new MockMultipartFile("file","other.txt","text/plain",new byte[]{1})));
   assertThatThrownBy(()->notices.save(null,input("USER",Set.of(a.id()),Set.of(other.id())))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
   assertThat(notices.page(page,null,null,null,null,false).total()).isEqualTo(1);
   Users.as(Users.principal(a.id(),234,DataAccess.own()),()->{assertThat(mvc.get().uri("/api/message/notices")).hasStatus(403);return null;});
   Users.as(Users.principal(101,235,DataAccess.unrestricted()),()->{assertThatThrownBy(()->notices.get(row.id())).isInstanceOf(BizException.class);assertThatThrownBy(()->notices.restore(row.id())).isInstanceOf(BizException.class);return null;});
   return null;
  });
 }
 @Test void bulkReadAndRecycleAreTransactionalAndNeverCrossTenantOrPortal(){
  Users.as(Users.principal(101,236,DataAccess.unrestricted()),()->{
   var a=user("bulk-reader",null,Set.of(),Set.of());var b=user("bulk-outsider",null,Set.of(),Set.of());
   var first=notices.save(null,input("USER",Set.of(a.id()),Set.of()));var second=notices.save(null,input("USER",Set.of(a.id()),Set.of()));
   var foreign=notices.save(null,input("USER",Set.of(b.id()),Set.of()));
   notices.deleteMany(Set.of(second.id(),foreign.id()));
   Users.as(Users.principal(a.id(),236,DataAccess.own()),()->{
    assertThat(notices.readAll()).isEqualTo(1);assertThat(notices.readAll()).isZero();return null;
   });
   assertThatThrownBy(()->notices.restoreMany(new LinkedHashSet<>(List.of(second.id(),999L)))).isInstanceOf(BizException.class);
   assertThat(notices.page(page,null,null,null,null,true).total()).isEqualTo(2);
   notices.restoreMany(Set.of(second.id(),foreign.id()));
   Users.as(Users.principal(a.id(),236,DataAccess.own()),()->{
    assertThat(notices.unreadCount()).isEqualTo(1);
    assertThatThrownBy(()->notices.readMany(new LinkedHashSet<>(List.of(second.id(),foreign.id())))).isInstanceOf(BizException.class);
    assertThat(notices.unreadCount()).isEqualTo(1);notices.readMany(Set.of(first.id(),second.id()));assertThat(notices.unreadCount()).isZero();return null;
   });return null;
  });
 }
}
