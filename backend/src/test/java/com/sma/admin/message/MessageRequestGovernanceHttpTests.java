package com.sma.admin.message;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.message.internal.service.*;
import com.sma.admin.support.*;
import com.sma.admin.workspace.internal.service.ShelfService;

@IntegrationTest @AutoConfigureMockMvc @RecordApplicationEvents
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.security.requests.minimum-write-interval=30s"})
class MessageRequestGovernanceHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserService users;
    @Autowired PrivateMessageService messages;
    @Autowired TodoService todos;
    @Autowired NoticeService notices;
    @Autowired ShelfService shelf;
    @Autowired ApplicationEvents events;

    @Test void repeatedPrivateSendCreatesOneMessageAndOneDomainEventWithoutReplayingItsBody() {
        long[] ids=Users.as(actor(79401,79401),()->new long[]{createUser("sender"),createUser("recipient"),createUser("outsider")});
        Users.as(actor(ids[0],79401),()->{
            String body="{\"recipientId\":\""+ids[1]+"\",\"content\":\"Private communication\"}", key=key();
            var first=send(write(post("/api/message/private"),key,body));success(first);
            long id=Long.parseLong(value(first,"/data/id"));
            var replay=send(write(post("/api/message/private"),key,body));completed(replay);
            assertThat(text(replay)).doesNotContain("Private communication","recipientId");
            assertThat(messages.page(new PageQuery(1,20,null,null),"outbox",null,null).total()).isEqualTo(1);
            assertThat(events.stream(PrivateMessageSentEvent.class).filter(event->event.messageId()==id).count()).isEqualTo(1);
            Users.as(actor(ids[2],79401),()->{
                assertThat(value(send(get("/api/message/private/"+id)),"/error")).isEqualTo("common.error.data-not-found");return null;
            });return null;
        });
    }

    @Test void mixedOwnerBatchRollsBackAllChangesAndKeepsAFinalFailureReceipt() {
        var input=new TodoService.Input("Atomic task",null,null,1,2,Instant.parse("2027-01-01T00:00:00Z"),null,null);
        long mine=Users.as(actor(79402,79402),()->todos.save(null,input,false).id());
        long other=Users.as(actor(79403,79402),()->todos.save(null,input,false).id());
        Users.as(actor(79402,79402),()->{
            String key=key(), body="{\"ids\":[\""+mine+"\",\""+other+"\"],\"state\":3}";
            var rejected=send(write(post("/api/message/todos/batch-state"),key,body));
            assertThat(value(rejected,"/error")).isEqualTo("common.error.data-not-found");
            assertThat(todos.get(mine).state()).isEqualTo(1);
            var replay=send(write(post("/api/message/todos/batch-state"),key,body));
            assertThat(replay.getResponse().getStatus()).isEqualTo(409);
            assertThat(value(replay,"/data/state")).isEqualTo("FAILED_FINAL");
            String single=key();success(send(write(put("/api/message/todos/"+mine+"/state"),single,"{\"state\":3}")));
            completed(send(write(put("/api/message/todos/"+mine+"/state"),single,"{\"state\":3}")));
            assertThat(todos.get(mine).state()).isEqualTo(3);return null;
        });
        Users.as(actor(79403,79402),()->{assertThat(todos.get(other).state()).isEqualTo(1);return null;});
    }

    @Test void shelfRetainsNativeCreateKeyVersionsAndPrivateOwnership() {
        Users.as(actor(79404,79404),()->{
            String nativeKey=key(), httpKey=key();
            var input=new ShelfService.NoteInput("Private note","text","Private note content",List.of(),"purple",false,null);
            var create=new ShelfService.CreateNote(nativeKey,input);
            String body=json.writeValueAsString(create);
            var first=send(write(post("/api/workspace/shelf/notes"),httpKey,body));success(first);
            long id=Long.parseLong(value(first,"/data/id"));
            var replay=send(write(post("/api/workspace/shelf/notes"),httpKey,body));completed(replay);
            assertThat(text(replay)).doesNotContain("Private note content","Private note");
            assertThat(shelf.create(create).id()).isEqualTo(id);
            assertThat(shelf.notes()).hasSize(1);
            var edit=new ShelfService.UpdateNote(1L,new ShelfService.NoteInput("Updated","text","Edited content",List.of(),"green",false,null));
            String editKey=key(), editBody=json.writeValueAsString(edit);
            success(send(write(put("/api/workspace/shelf/notes/"+id),editKey,editBody)));
            completed(send(write(put("/api/workspace/shelf/notes/"+id),editKey,editBody)));
            assertThat(shelf.getNote(id).version()).isEqualTo(2L);
            var stale=send(write(put("/api/workspace/shelf/notes/"+id+"/status"),key(),"{\"version\":1,\"status\":\"archived\"}"));
            assertThat(value(stale,"/error")).isEqualTo("workspace.error.conflict");
            assertThat(shelf.getNote(id).status()).isEqualTo("active");
            Users.as(actor(79405,79404),()->{
                assertThat(value(send(get("/api/workspace/shelf/notes/"+id)),"/error")).isEqualTo("common.error.not-found");return null;
            });return null;
        });
    }

    @Test void noticeReplayDoesNotRepublishOrRepeatRecipientSnapshot() {
        var actor=new SmaPrincipal(79406,"notices",79406,"admin",null,false,DataAccess.unrestricted(),Set.of("message:notice:create","message:notice:view"),"UTC");
        Users.as(actor,()->{
            long recipient=createUser("notice-recipient");
            String body="{\"title\":\"Governed notice\",\"content\":\"<p>Notice body</p>\",\"type\":\"1\",\"priority\":1,\"audienceType\":\"USER\",\"audienceIds\":[\""+recipient+"\"],\"fileIds\":[]}";
            String key=key();var first=send(write(post("/api/message/notices"),key,body));success(first);
            long id=Long.parseLong(value(first,"/data/id"));
            completed(send(write(post("/api/message/notices"),key,body)));
            assertThat(notices.get(id).recipientCount()).isEqualTo(1);
            assertThat(events.stream(NoticePublishedEvent.class).filter(event->event.noticeId()==id).count()).isEqualTo(1);
            Users.as(actor(recipient,79406),()->{
                assertThat(notices.inbox(new PageQuery(1,20,null,null),null).total()).isEqualTo(1);return null;
            });return null;
        });
    }

    private long createUser(String username){return users.create(new UserService.Create(new UserService.Input(username,username,null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,"UTC"),"m7K2v9R4")).id();}
    private static SmaPrincipal actor(long id,long tenant){return new SmaPrincipal(id,"message-governance",tenant,"admin",null,false,DataAccess.unrestricted(),Set.of(),"UTC");}
    private static String key(){return UUID.randomUUID().toString();}
    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request,String key,String body){return request.header("Idempotency-Key",key).contentType("application/json").content(body);}
    private MvcResult send(MockHttpServletRequestBuilder request){var user=CurrentUser.require();return Users.as(user,()->{try{return mvc.perform(request.with(Users.request(user))).andReturn();}catch(Exception ex){throw new IllegalStateException(ex);}});}
    private String text(MvcResult response){try{return response.getResponse().getContentAsString();}catch(Exception ex){throw new IllegalStateException(ex);}}
    private String value(MvcResult response,String pointer){return json.readTree(text(response)).at(pointer).asText();}
    private void success(MvcResult response){assertThat(response.getResponse().getStatus()).as(text(response)).isEqualTo(200);assertThat(value(response,"/code")).as(text(response)).isEqualTo("0");}
    private void completed(MvcResult response){assertThat(response.getResponse().getStatus()).as(text(response)).isEqualTo(409);assertThat(value(response,"/data/state")).isEqualTo("SUCCEEDED");}
}
