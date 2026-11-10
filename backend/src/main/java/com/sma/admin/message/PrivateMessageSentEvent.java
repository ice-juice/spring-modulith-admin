package com.sma.admin.message;
public record PrivateMessageSentEvent(long tenantId,String portal,long messageId,long senderId,long recipientId){}
