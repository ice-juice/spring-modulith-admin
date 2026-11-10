package com.sma.admin.message.internal.service;
import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.security.DashboardContributor;
@Component
public class MessageDashboardContributor implements DashboardContributor {
 private final MessageSummaryService summary;private final NoticeService notices;private final TodoService todos;
 public MessageDashboardContributor(MessageSummaryService summary,NoticeService notices,TodoService todos){this.summary=summary;this.notices=notices;this.todos=todos;}
 public Contribution contribute(){var counts=summary.get();List<Entry> entries=new ArrayList<>();
  notices.inbox(new PageQuery(1,5,null,null),true).items().forEach(row->entries.add(new Entry("message.dashboard.notice",row.title(),"/message/inbox?tab=notices&focus="+row.id(),row.createdAt())));
  todos.page(new PageQuery(1,5,null,null),null,null,null,null,true,null,null,null,false).items().forEach(row->entries.add(new Entry("message.dashboard.todo",row.title(),"/message/inbox?tab=tasks&focus="+row.id(),row.dueAt())));
  return new Contribution(List.of(new Metric("message.notices","message.dashboard.notices",counts.unreadNotices(),"/message/inbox?tab=notices"),new Metric("message.todos","message.dashboard.todos",counts.pendingTodos(),"/message/inbox?tab=tasks"),new Metric("message.private","message.dashboard.private",counts.unreadMessages(),"/message/inbox?tab=private")),entries);
 }
}
