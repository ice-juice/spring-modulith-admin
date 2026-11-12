package com.sma.admin.message.internal.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.deployment.*;

@Component
public class SocketHub extends TextWebSocketHandler implements DeploymentConnections, AutoCloseable {
    public static final String HANDSHAKE_ATTRIBUTE="sma.socket-handshake";
    private static final Logger log=LoggerFactory.getLogger(SocketHub.class);
    private static final long VALIDATE_NANOS=TimeUnit.SECONDS.toNanos(30);
    private static final int BATCH=128;
    private final SocketTicketService tickets;
    private final SocketProperties properties;
    private final JsonMapper json;
    private final DeploymentWork admission;
    private final ExecutorService worker;
    private final LongSupplier clock;
    private final AtomicBoolean queued=new AtomicBoolean();
    private final Map<String,Connection> connections=new ConcurrentHashMap<>();
    // Protect handoff, counters and close decisions; never hold this monitor across I/O.
    private final Set<Handshake> handshakes=new HashSet<>();
    private final ArrayDeque<Connection> maintenance=new ArrayDeque<>();
    private int operations;
    private volatile boolean closed;

    private static final class Connection {
        final WebSocketSession raw;
        final ConcurrentWebSocketSessionDecorator sending;
        final SocketTicketService.Grant grant;
        volatile boolean closing;
        CloseStatus closeStatus;
        String reason;
        boolean closeInitiated;
        int operations;
        long nextValidation;
        Connection(WebSocketSession raw,SocketTicketService.Grant grant,long nextValidation){
            this.raw=raw;this.grant=grant;this.nextValidation=nextValidation;
            sending=new ConcurrentWebSocketSessionDecorator(raw,5000,64*1024);
        }
    }
    /** Opaque reservation: only this hub can hand it off to a connection. */
    public final class Handshake implements AutoCloseable {
        private Handshake(){}
        @Override public void close(){synchronized(SocketHub.this){handshakes.remove(this);}}
    }
    public record Audience(long tenantId,String portal,long userId){}

    @Autowired
    public SocketHub(SocketTicketService tickets,SocketProperties properties,JsonMapper json,DeploymentWork admission){
        this(tickets,properties,json,admission,new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-socket-lifecycle-",0).factory(),
                new ThreadPoolExecutor.AbortPolicy()),System::nanoTime);
    }
    SocketHub(SocketTicketService tickets,SocketProperties properties,JsonMapper json,DeploymentWork admission,
              ExecutorService worker,LongSupplier clock){
        this.tickets=tickets;this.properties=properties;this.json=json;this.admission=admission;this.worker=worker;this.clock=clock;
    }
    @Override public String id(){return "message.websocket";}
    @Override public synchronized Snapshot snapshot(){return new Snapshot(handshakes.size(),connections.size(),operations);}

    /** HTTP admission is an observation, not a host-only background-work permit. */
    public synchronized Optional<Handshake> beginHandshake(){
        if(closed||!admission.snapshot().accepting()||handshakes.size()+connections.size()>=properties.maxConnections())return Optional.empty();
        var handshake=new Handshake();handshakes.add(handshake);return Optional.of(handshake);
    }
    public Set<Audience> subscriptions(){
        return connections.values().stream().filter(c->!c.closing&&c.grant!=null)
                .map(c->new Audience(c.grant.tenantId(),c.grant.portal(),c.grant.userId()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    @Override public void afterConnectionEstablished(WebSocketSession session)throws Exception{
        var value=session.getAttributes().get("sma.socket-grant");
        var grant=value instanceof SocketTicketService.Grant g?g:null;
        var connection=new Connection(session,grant,clock.getAsLong()+VALIDATE_NANOS);
        synchronized(this){
            boolean reserved=handshakes.remove(session.getAttributes().get(HANDSHAKE_ATTRIBUTE));
            long count=grant==null?0:connections.values().stream().filter(c->c.grant!=null&&c.grant.tenantId()==grant.tenantId()&&c.grant.sessionId()==grant.sessionId()).count();
            connections.put(session.getId(),connection);
            maintenance.addLast(connection);
            if(draining()||!admission.snapshot().accepting())requestClose(connection,CloseStatus.GOING_AWAY,null);
            else if(!reserved||grant==null||count>=8)requestClose(connection,CloseStatus.POLICY_VIOLATION,null);
        }
        try(var operation=start(connection)){
            if(operation==null)return;
            if(!tickets.active(grant)){requestClose(connection,CloseStatus.POLICY_VIOLATION,null);return;}
            send(connection,new TextMessage(json.writeValueAsString(Map.of("type","connected"))));
        }finally{if(closed)closeReady(connection);}
    }
    @Override public synchronized void afterConnectionClosed(WebSocketSession session,CloseStatus status){
        var removed=connections.remove(session.getId());if(removed!=null)maintenance.remove(removed);
    }
    @Override public void handleTransportError(WebSocketSession session,Throwable error){requestClose(connections.get(session.getId()),CloseStatus.POLICY_VIOLATION,"TRANSPORT_ERROR");}
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage message){requestClose(connections.get(session.getId()),CloseStatus.NOT_ACCEPTABLE,null);}

    public void push(long tenantId,String portal,Set<Long> userIds,String type,long id){
        for(var connection:connections.values()){
            var grant=connection.grant;
            if(grant==null||grant.tenantId()!=tenantId||!grant.portal().equals(portal)||!userIds.contains(grant.userId()))continue;
            try(var operation=start(connection)){
                if(operation==null)continue;
                if(!tickets.active(grant)){requestClose(connection,CloseStatus.POLICY_VIOLATION,"SESSION_INVALID");continue;}
                send(connection,new TextMessage(json.writeValueAsString(Map.of("type",type,"id",Long.toString(id)))));
            }
        }
    }
    public void revalidate(Audience audience,long lastSessionId,String reason){
        for(var connection:connections.values()){
            var grant=connection.grant;
            if(grant==null||grant.tenantId()!=audience.tenantId()||grant.userId()!=audience.userId()||!grant.portal().equals(audience.portal()))continue;
            try(var operation=start(connection)){
                if(operation!=null&&!tickets.active(grant))requestClose(connection,CloseStatus.POLICY_VIOLATION,grant.sessionId()==lastSessionId?reason:"SESSION_INVALID");
            }
        }
    }
    public void endSession(long tenantId,long sessionId,String reason){
        for(var connection:connections.values())if(connection.grant!=null&&connection.grant.tenantId()==tenantId&&connection.grant.sessionId()==sessionId)
            requestClose(connection,CloseStatus.POLICY_VIOLATION,reason);
    }

    @Scheduled(fixedDelay=250) public void tick(){
        DeploymentEvidence.requireHost();
        if(closed||!queued.compareAndSet(false,true))return;
        try{worker.execute(()->{try{maintainOnce();}finally{queued.set(false);}});}
        catch(RejectedExecutionException rejected){queued.set(false);}
    }
    void maintainOnce(){
        DeploymentEvidence.requireHost();
        int budget;
        synchronized(this){budget=Math.min(BATCH,maintenance.size());}
        for(int i=0;i<budget;i++){
            Connection connection;
            synchronized(this){connection=maintenance.pollFirst();if(connection==null)break;maintenance.addLast(connection);}
            if(draining())requestClose(connection,CloseStatus.GOING_AWAY,null);
            if(connection.closing){closeReady(connection);continue;}
            if(clock.getAsLong()-connection.nextValidation<0)continue;
            var accepted=admission.tryStart();if(accepted.isEmpty())continue;
            try(var permit=accepted.orElseThrow();var operation=start(connection)){
                if(operation==null)continue;
                connection.nextValidation=clock.getAsLong()+VALIDATE_NANOS;
                if(!tickets.active(connection.grant))requestClose(connection,CloseStatus.POLICY_VIOLATION,"SESSION_INVALID");
                else send(connection,new PingMessage());
            }catch(Exception failure){requestClose(connection,CloseStatus.POLICY_VIOLATION,"TRANSPORT_ERROR");}
        }
    }
    private boolean draining(){return closed||admission.snapshot().draining();}
    private synchronized Operation start(Connection connection){
        if(connection==null||connections.get(connection.raw.getId())!=connection||connection.closing)return null;
        if(draining()){requestClose(connection,CloseStatus.GOING_AWAY,null);return null;}
        operations++;connection.operations++;return new Operation(connection);
    }
    private final class Operation implements AutoCloseable {
        private final Connection connection;private boolean released;
        Operation(Connection connection){this.connection=connection;}
        @Override public void close(){
            synchronized(SocketHub.this){if(released)return;released=true;operations--;connection.operations--;}
            if(closed)closeReady(connection);
        }
    }
    private void send(Connection connection,WebSocketMessage<?> message){
        try{connection.sending.sendMessage(message);}
        catch(Exception failure){log.debug("Socket delivery failed");requestClose(connection,CloseStatus.POLICY_VIOLATION,"TRANSPORT_ERROR");}
    }
    private synchronized void requestClose(Connection connection,CloseStatus status,String reason){
        if(connection==null||connections.get(connection.raw.getId())!=connection||connection.closeInitiated)return;
        if(draining()){status=CloseStatus.GOING_AWAY;reason=null;}
        if(!connection.closing||status.equals(CloseStatus.GOING_AWAY)){
            connection.closing=true;connection.closeStatus=status;connection.reason=reason;
        }
    }
    /** Closing already-owned transports is technical coordination, not new business work. */
    private boolean closeReady(Connection connection){
        CloseStatus status;String reason;
        synchronized(this){
            if(connections.get(connection.raw.getId())!=connection||!connection.closing||connection.closeInitiated||connection.operations!=0)return false;
            status=draining()?CloseStatus.GOING_AWAY:connection.closeStatus;reason=draining()?null:connection.reason;
            connection.closeInitiated=true;connection.operations++;operations++;
        }
        boolean failed=false;
        try{
            if(reason!=null&&connection.raw.isOpen())try{connection.raw.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","session-ended","reason",reason))));}
            catch(Exception unavailable){log.debug("Socket terminal hint unavailable");}
            connection.raw.close(status);
        }catch(Exception unavailable){failed=true;log.debug("Socket close unavailable");}
        finally{synchronized(this){connection.operations--;operations--;if(failed)connection.closeInitiated=false;}}
        // Only afterConnectionClosed removes a connection; close() returning is not that proof.
        return true;
    }
    @PreDestroy @Override public void close(){
        closed=true;
        for(var connection:connections.values())requestClose(connection,CloseStatus.GOING_AWAY,null);
        worker.shutdownNow();
        for(var connection:connections.values())closeReady(connection);
    }
}
