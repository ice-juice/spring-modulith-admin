package com.sma.admin.common;
import java.time.Instant;
public record OperationAuditEvent(String eventId,long tenantId,long userId,Long departmentId,String username,String portal,
 String action,String messageKey,String objectId,String clientPath,String requestPath,int resultCode,String error,long durationMs,
 String ip,String userAgent,Instant occurredAt) {}
