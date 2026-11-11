package com.sma.admin.message.internal.service;
import java.util.Set;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.auth.SessionEndedEvent;
import com.sma.admin.message.*;
@Component
public class SocketEventListener {
 private final SocketSignalStore signals;public SocketEventListener(SocketSignalStore signals){this.signals=signals;}
 @ApplicationModuleListener @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
 public void notice(NoticePublishedEvent event){publish(event.tenantId(),event.portal(),event.userIds(),"notice",event.noticeId(),null);}
 @ApplicationModuleListener @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
 public void todo(TodoChangedEvent event){publish(event.tenantId(),event.portal(),Set.of(event.userId()),"todo",event.todoId(),null);}
 @ApplicationModuleListener @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
 public void privateMessage(PrivateMessageSentEvent event){publish(event.tenantId(),event.portal(),Set.copyOf(java.util.List.of(event.senderId(),event.recipientId())),"private-message",event.messageId(),null);}
 @ApplicationModuleListener @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=10)
 public void ended(SessionEndedEvent event){publish(event.tenantId(),"*",Set.of(event.userId()),"session-ended",event.sessionId(),event.reason());}
 private void publish(long tenant,String portal,Set<Long> users,String type,long id,String reason){
  ActorContext.withinJob(tenant,"message-signal:"+type,()->{signals.publish(portal,users,type,id,reason);return null;});
 }
}
