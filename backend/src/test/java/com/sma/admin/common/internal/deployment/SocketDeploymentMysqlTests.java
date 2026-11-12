package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.message.internal.service.*;
import com.sma.admin.support.*;
import static com.sma.admin.support.DeploymentWorkFixture.host;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SocketDeploymentMysqlTests {
    @LocalServerPort int port;
    @Autowired com.sma.admin.tenant.internal.persistence.DomainMapper domains;
    @Autowired UserService users;
    @Autowired SessionIssuer issuer;
    @Autowired SessionService sessions;
    @Autowired SocketTicketService tickets;
    @Autowired SocketHub hub;
    @Autowired DeploymentWorkCoordinator work;
    static final long TENANT=98740;
    static class Inbox implements WebSocket.Listener {
        final BlockingQueue<String> messages=new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> close=new CompletableFuture<>();
        final StringBuilder text=new StringBuilder();
        public void onOpen(WebSocket socket){socket.request(1);}
        public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){text.append(data);if(last){messages.add(text.toString());text.setLength(0);}socket.request(1);return null;}
        public CompletionStage<?> onClose(WebSocket socket,int status,String reason){close.complete(status);return null;}
    }
    @Test void actualTomcatDrainCloses1001WithoutRevocationAndOriginalSessionCanGetFreshTicket()throws Exception{
        long[] ids=new long[2];String[] token=new String[1];SocketTicketService.Ticket[] initial=new SocketTicketService.Ticket[1];
        Users.as(Users.principal(101,TENANT,DataAccess.unrestricted()),()->{
            var domain=new com.sma.admin.tenant.internal.persistence.DomainEntity();domain.setOwnerTenantId(TENANT);domain.setHostname("127.0.0.3");domain.setPortal("admin");
            domain.setStatus("ACTIVE");domain.setPrimaryDomain(false);domain.setVerificationStatus("VERIFIED");domain.setTlsStatus("READY");domain.setDnsStatus("READY");domain.setVerifiedAt(Instant.now());domain.setTlsValidUntil(Instant.now().plusSeconds(86400));domain.setProvenance("TEST");domains.insert(domain);
            var user=users.create(new UserService.Create(new UserService.Input("socket-drain","Socket Drain",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Q7d42d97c0"));ids[0]=user.id();
            Users.as(Users.principal(user.id(),TENANT,DataAccess.unrestricted()),()->{
                var login=issuer.issue(users.findById(user.id()).orElseThrow(),false,Duration.ofMinutes(5),"127.0.0.1","test");token[0]=login.token();
                ids[1]=sessions.resolve(token[0]).orElseThrow().session().getId();initial[0]=tickets.issue(ids[1]);return null;
            });return null;
        });
        var actor=Users.principal(ids[0],TENANT,DataAccess.unrestricted());
        try(var client=HttpClient.newHttpClient()){
            var inbox=new Inbox();var socket=client.newWebSocketBuilder().header("Origin","http://127.0.0.3:"+port)
                    .buildAsync(URI.create("ws://127.0.0.3:"+port+"/api/message/ws?ticket="+initial[0].ticket()),inbox).get(10,TimeUnit.SECONDS);
            try{
                assertThat(inbox.messages.poll(10,TimeUnit.SECONDS)).contains("connected");assertThat(hub.snapshot().connections()).isOne();assertThat(hub.snapshot().handshakes()).isZero();
                assertThat(host(work::beginDrain)).isTrue();
                assertThat(inbox.close.get(10,TimeUnit.SECONDS)).isEqualTo(1001);
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(()->hub.snapshot().drained());
                assertThat(inbox.messages).noneMatch(message->message.contains("session-ended"));
                var fresh=Users.as(actor,()->{assertThat(sessions.resolve(token[0])).isPresent();return tickets.issue(ids[1]);});
                assertThatThrownBy(()->client.newWebSocketBuilder().header("Origin","http://127.0.0.3:"+port)
                        .buildAsync(URI.create("ws://127.0.0.3:"+port+"/api/message/ws?ticket="+fresh.ticket()),new Inbox()).join())
                        .hasCauseInstanceOf(WebSocketHandshakeException.class);
                // A rejected draining-node handshake did not consume the newly issued shared ticket.
                Users.as(actor,()->{assertThat(tickets.consume(fresh.ticket())).isPresent();assertThat(tickets.consume(fresh.ticket())).isEmpty();
                    assertThat(sessions.resolve(token[0])).isPresent();return null;});
                assertThat(hub.snapshot().drained()).isTrue();assertThat(work.snapshot().activeWork()).isZero();
            }finally{socket.abort();}
        }
    }
}
