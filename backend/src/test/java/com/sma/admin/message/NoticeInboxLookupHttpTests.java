package com.sma.admin.message;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.message.internal.service.NoticeService;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class NoticeInboxLookupHttpTests {
    @Autowired NoticeService notices;
    @Autowired UserService users;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    @Test void recipientLookupIsReadOnlyAndDoesNotRequireManagementPermission(){
        Users.as(Users.principal(79821,79821,DataAccess.unrestricted()),()->{
            long recipient=user("readonly-recipient");
            var notice=notices.save(null,input(Set.of(recipient)));
            Users.as(Users.principal(recipient,79821,DataAccess.own()),()->{
                var first=read(notice.id());
                assertThat(first.getResponse().getStatus()).isEqualTo(200);
                assertThat(first.getResponse().getHeader("X-Request-Policy")).isEqualTo("QUERY");
                assertThat(first.getResponse().getHeader("X-Operation-Id")).isNull();
                assertThat(tree(first).at("/code").asInt()).isZero();
                assertThat(tree(first).at("/data/id").asText()).isEqualTo(notice.id().toString());
                assertThat(tree(first).at("/data/readAt").isNull()).isTrue();
                read(notice.id());assertThat(notices.unreadCount()).isEqualTo(1);
                var marked=notices.read(notice.id());
                assertThat(notices.inboxNotice(notice.id()).readAt()).isEqualTo(marked.readAt());
                assertThat(tree(read(notice.id())).at("/data/readAt").asText()).isEqualTo(marked.readAt().toString());
                return null;
            });return null;
        });
    }
    @Test void currentRecipientTenantPortalAndRecycleStateAreCheckedOnEveryLookup(){
        Users.as(Users.principal(79822,79822,DataAccess.unrestricted()),()->{
            long recipient=user("lookup-recipient"), outsider=user("lookup-outsider");
            var notice=notices.save(null,input(Set.of(recipient)));
            for(var actor:java.util.List.of(Users.principal(outsider,79822,DataAccess.unrestricted()),
                    Users.principal(recipient,79823,DataAccess.unrestricted()),
                    new SmaPrincipal(recipient,"other-portal",79822,"other",null,false,DataAccess.unrestricted(),Set.of(),"UTC")))
                Users.as(actor,()->{denied(notice.id());return null;});
            notices.delete(notice.id());Users.as(Users.principal(recipient,79822,DataAccess.own()),()->{denied(notice.id());return null;});
            notices.restore(notice.id());
            notices.save(notice.id(),input(Set.of(outsider)));
            Users.as(Users.principal(recipient,79822,DataAccess.own()),()->{denied(notice.id());return null;});
            return null;
        });
    }
    private void denied(long id){var result=read(id);assertThat(tree(result).at("/error").asText()).isEqualTo("common.error.data-not-found");assertThat(text(result)).doesNotContain("Recipient-only content");}
    private NoticeService.Input input(Set<Long> recipients){return new NoticeService.Input("Recipient-only content","<p>Authorized content</p>","1",1,"USER",recipients,Set.of());}
    private long user(String name){return users.create(new UserService.Create(new UserService.Input(name,name,null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,"UTC"),"m7K2v9R4")).id();}
    private MvcResult read(long id){var actor=CurrentUser.require();return Users.as(actor,()->{try{return mvc.perform(get("/api/message/notices/inbox/"+id).with(Users.request(actor))).andReturn();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private String text(MvcResult result){try{return result.getResponse().getContentAsString();}catch(Exception failure){throw new IllegalStateException(failure);}}
    private tools.jackson.databind.JsonNode tree(MvcResult result){return json.readTree(text(result));}
}
