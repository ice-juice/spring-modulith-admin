package com.sma.admin.message;
import java.util.Set;
public record NoticePublishedEvent(long tenantId,String portal,long noticeId,Set<Long> userIds){
 public NoticePublishedEvent{userIds=Set.copyOf(userIds);}
}
