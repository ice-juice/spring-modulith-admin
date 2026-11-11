package com.sma.admin.message.internal.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.persistence.*;

/** Latest per-recipient hints, never a competing delivery queue or a source of authorization. */
@Service @EnableConfigurationProperties(SocketSignalProperties.class)
public class SocketSignalStore {
    private static final Set<String> TYPES=Set.of("notice","todo","private-message","session-ended");
    private final SocketSignalMapper signals;private final SocketSignalGuardMapper guards;private final SocketSignalProperties settings;
    public SocketSignalStore(SocketSignalMapper signals,SocketSignalGuardMapper guards,SocketSignalProperties settings){this.signals=signals;this.guards=guards;this.settings=settings;}
    public record Signal(long id,String portal,long userId,String type,long objectId,long revision,String reason) {}

    @Transactional(propagation=Propagation.MANDATORY)
    public void publish(String portal,Set<Long> userIds,String type,long objectId,String reason){
        worker();
        boolean ended="session-ended".equals(type);
        if(type==null||!TYPES.contains(type)||objectId<1||portal==null||!(ended?portal.equals("*"):portal.matches("[a-z][a-z0-9-]{0,31}"))
                ||userIds==null||userIds.size()>10000||userIds.stream().anyMatch(id->id==null||id<1)
                ||(ended?(reason==null||!reason.matches("[A-Z_]{1,32}")):reason!=null))throw new IllegalArgumentException("Invalid socket signal");
        if(userIds.isEmpty())return;
        guards.ensure(IdWorker.getId(),CurrentUser.tenantId());
        Objects.requireNonNull(guards.selectOne(new LambdaQueryWrapper<SocketSignalGuardEntity>().last("FOR UPDATE")),"Signal guard unavailable");
        signals.purgeExpired();
        var users=userIds.stream().sorted().toList();long existing=0;
        for(int offset=0;offset<users.size();offset+=500)existing+=signals.selectCount(new LambdaQueryWrapper<SocketSignalEntity>()
                .eq(SocketSignalEntity::getPortal,portal).eq(SocketSignalEntity::getSignalType,type).in(SocketSignalEntity::getUserId,users.subList(offset,Math.min(users.size(),offset+500))));
        if(signals.selectCount(null)+users.size()-existing>settings.maxEntries())throw new IllegalStateException("Socket signal capacity exhausted; retain publication and use authorized REST refresh");
        var expires=signals.now().plus(settings.retention());
        for(int offset=0;offset<users.size();offset+=500){
            var rows=new ArrayList<SocketSignalEntity>();
            for(long user:users.subList(offset,Math.min(users.size(),offset+500))){
                var row=new SocketSignalEntity();row.setId(IdWorker.getId());row.setPortal(portal);row.setUserId(user);row.setSignalType(type);row.setObjectId(objectId);row.setReason(reason);row.setExpiresAt(expires);rows.add(row);
            }
            signals.coalesce(CurrentUser.tenantId(),rows);
        }
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.READ_COMMITTED,timeout=2)
    public List<Signal> read(String portal,Set<Long> users){
        worker();if(portal==null||!portal.matches("[a-z][a-z0-9-]{0,31}")||users==null||users.isEmpty()||users.size()>1000||users.stream().anyMatch(id->id==null||id<1))throw new IllegalArgumentException("Invalid socket subscription batch");
        return signals.selectList(new LambdaQueryWrapper<SocketSignalEntity>().in(SocketSignalEntity::getPortal,List.of(portal,"*"))
                .in(SocketSignalEntity::getUserId,users).gt(SocketSignalEntity::getExpiresAt,signals.now()))
                .stream().map(row->new Signal(row.getId(),row.getPortal(),row.getUserId(),row.getSignalType(),row.getObjectId(),row.getRevision(),row.getReason())).toList();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=2)
    public int purge(){
        worker();var guard=guards.selectOne(new LambdaQueryWrapper<SocketSignalGuardEntity>().last("FOR UPDATE NOWAIT"));
        return guard==null?0:signals.purgeExpired();
    }
    private static void worker(){
        var actor=ActorContext.require();
        if(actor.kind()!=ActorRef.Kind.SYSTEM_JOB||!(actor.subject().startsWith("message-signal:")||actor.subject().startsWith("message-socket:")))throw new AccessDeniedException("Trusted message signal work required");
    }
}
