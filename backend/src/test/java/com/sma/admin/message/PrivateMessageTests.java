package com.sma.admin.message;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.message.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
class PrivateMessageTests {
 @Autowired PrivateMessageService messages;@Autowired UserService users;@Autowired MessageSummaryService summary;
 private final PageQuery page=new PageQuery(1,20,null,null);
 private UserService.View user(String username){return users.create(new UserService.Create(new UserService.Input(username,username,null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Q7a23d9367"));}
 @Test void onlyParticipantsCanReadAndOnlyRecipientCanMarkReadAndCountsArePersonal(){
  Users.as(Users.principal(101,245,DataAccess.unrestricted()),()->{
   var a=user("sender");var b=user("receiver");var c=user("outsider");
   var row=Users.as(Users.principal(a.id(),245,DataAccess.own()),()->{
    var message=messages.send(new PrivateMessageService.Input(b.id(),"<b>plain text</b>"));
    assertThat(messages.page(page,"outbox",null,null).total()).isEqualTo(1);
    assertThatThrownBy(()->messages.read(message.id())).isInstanceOf(BizException.class);return message;
   });
   Users.as(Users.principal(c.id(),245,DataAccess.unrestricted()),()->{
    assertThatThrownBy(()->messages.get(row.id())).isInstanceOf(BizException.class);assertThat(messages.page(page,"conversation",a.id(),null).total()).isZero();return null;
   });
   Users.as(Users.principal(b.id(),245,DataAccess.own()),()->{
    assertThat(messages.recipients("sender")).anySatisfy(option->assertThat(option.value()).isEqualTo(a.id()));
    assertThat(summary.get().unreadMessages()).isEqualTo(1);assertThat(summary.get().unreadNotices()).isZero();
    assertThat(messages.get(row.id()).content()).isEqualTo("<b>plain text</b>");
    var read=messages.read(row.id()).readAt();assertThat(read).isNotNull();assertThat(messages.read(row.id()).readAt()).isEqualTo(read);
    assertThat(messages.readAll()).isZero();assertThat(summary.get().unreadMessages()).isZero();return null;
   });
   Users.as(Users.principal(a.id(),246,DataAccess.unrestricted()),()->{
    assertThatThrownBy(()->messages.get(row.id())).isInstanceOf(BizException.class);assertThatThrownBy(()->messages.send(new PrivateMessageService.Input(b.id(),"no"))).isInstanceOf(BizException.class);return null;
   });
   Users.as(new SmaPrincipal(a.id(),"sender",245,"other",null,false,DataAccess.unrestricted(),Set.of(),"UTC"),()->{
    assertThatThrownBy(()->messages.get(row.id())).isInstanceOf(BizException.class);assertThatThrownBy(()->messages.send(new PrivateMessageService.Input(b.id(),"no"))).isInstanceOf(BizException.class);return null;
   });return null;
  });
 }
}
