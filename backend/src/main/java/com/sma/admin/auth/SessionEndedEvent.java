package com.sma.admin.auth;
public record SessionEndedEvent(long tenantId,long userId,long sessionId,String reason) {}
