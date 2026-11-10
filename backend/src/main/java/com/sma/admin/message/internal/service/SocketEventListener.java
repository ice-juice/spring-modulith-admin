package com.sma.admin.message.internal.service;
import java.util.Set;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import com.sma.admin.auth.SessionEndedEvent;
import com.sma.admin.message.*;
@Component
public class SocketEventListener {
 private final SocketHub hub;public SocketEventListener(SocketHub hub){this.hub=hub;}
 @ApplicationModuleListener public void notice(NoticePublishedEvent event){hub.push(event.tenantId(),event.portal(),event.userIds(),"notice",event.noticeId());}
 @ApplicationModuleListener public void todo(TodoChangedEvent event){hub.push(event.tenantId(),event.portal(),Set.of(event.userId()),"todo",event.todoId());}
 @ApplicationModuleListener public void privateMessage(PrivateMessageSentEvent event){hub.push(event.tenantId(),event.portal(),Set.copyOf(java.util.List.of(event.senderId(),event.recipientId())),"private-message",event.messageId());}
 @ApplicationModuleListener public void ended(SessionEndedEvent event){hub.endSession(event.tenantId(),event.sessionId(),event.reason());}
}
