package com.sma.admin.message.internal.service;
import org.springframework.stereotype.Service;
@Service
public class MessageSummaryService {
 private final NoticeService notices;private final TodoService todos;private final PrivateMessageService messages;
 public MessageSummaryService(NoticeService notices,TodoService todos,PrivateMessageService messages){this.notices=notices;this.todos=todos;this.messages=messages;}
 public record View(long unreadNotices,long pendingTodos,long unreadMessages){}
 public View get(){return new View(notices.unreadCount(),todos.pendingCount(),messages.unreadCount());}
}
