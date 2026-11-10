package com.sma.admin.message;
public record TodoChangedEvent(long tenantId,String portal,long todoId,long userId,String action){}
