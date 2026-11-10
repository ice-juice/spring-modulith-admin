package com.sma.admin.message;
import static org.assertj.core.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.message.internal.service.*;
import com.sma.admin.support.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("test")
class SocketTests {
 @LocalServerPort int port;
 @Autowired UserService users;@Autowired SessionIssuer issuer;@Autowired SessionService sessions;@Autowired SessionManager manager;
 @Autowired SocketTicketService tickets;@Autowired NoticeService notices;@Autowired TodoService todos;@Autowired PrivateMessageService messages;
 static class Inbox implements WebSocket.Listener {
  final BlockingQueue<String> messages=new LinkedBlockingQueue<>();private final StringBuilder text=new StringBuilder();
  public void onOpen(WebSocket socket){socket.request(1);}
  public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){text.append(data);if(last){messages.add(text.toString());text.setLength(0);}socket.request(1);return null;}
 }
 @Test void realSocketDeliversCommittedMessagesAndForcedLogoutClosesOnlyItsSession()throws Exception{
  long[] ids=new long[2];SocketTicketService.Ticket[] grant=new SocketTicketService.Ticket[1];
  Users.as(Users.principal(101,252,DataAccess.unrestricted()),()->{
   var user=users.create(new UserService.Create(new UserService.Input("socket-reader","Socket Reader",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Socket@Secure2026"));
   ids[0]=user.id();
   Users.as(Users.principal(user.id(),252,DataAccess.unrestricted()),()->{
    var account=users.findById(user.id()).orElseThrow();var login=issuer.issue(account,false,Duration.ofMinutes(5),"127.0.0.1","test");
    ids[1]=sessions.resolve(login.token()).orElseThrow().session().getId();grant[0]=tickets.issue(ids[1]);return null;
   });return null;
  });
  try(var client=HttpClient.newHttpClient()){
   var inbox=new Inbox();var socket=client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:"+port+"/api/message/ws?ticket="+grant[0].ticket()),inbox).join();
   try{
    assertThat(inbox.messages.poll(10,TimeUnit.SECONDS)).contains("\"type\":\"connected\"");
    assertThatThrownBy(()->client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:"+port+"/api/message/ws?ticket="+grant[0].ticket()),new Inbox()).join()).hasCauseInstanceOf(WebSocketHandshakeException.class);
    Users.as(Users.principal(ids[0],252,DataAccess.unrestricted()),()->{
     notices.save(null,new NoticeService.Input("Socket notice","<p>Content</p>","1",1,"USER",Set.of(ids[0]),Set.of()));return null;
    });
    assertThat(inbox.messages.poll(10,TimeUnit.SECONDS)).contains("\"type\":\"notice\"");
    Users.as(Users.principal(ids[0],252,DataAccess.unrestricted()),()->{
     todos.save(null,new TodoService.Input("Socket task",null,null,1,1,Instant.now().plusSeconds(3600),null,null),false);return null;
    });
    assertThat(inbox.messages.poll(10,TimeUnit.SECONDS)).contains("\"type\":\"todo\"");
    Users.as(Users.principal(ids[0],252,DataAccess.unrestricted()),()->{messages.send(new PrivateMessageService.Input(ids[0],"Self message"));return null;});
    assertThat(inbox.messages.poll(10,TimeUnit.SECONDS)).contains("\"type\":\"private-message\"");
    Users.as(Users.principal(ids[0],252,DataAccess.unrestricted()),()->{manager.forceLogout(ids[1]);return null;});
    assertThat(inbox.messages.poll(10,TimeUnit.SECONDS)).contains("\"type\":\"session-ended\"","FORCED");
    org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(socket::isInputClosed);
   }finally{socket.abort();}
  }
 }
}
