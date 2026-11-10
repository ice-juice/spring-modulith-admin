package com.sma.admin.auth;
import java.time.Instant;
public record LoginEvent(String eventId,long tenantId,Long userId,Long departmentId,String username,String portal,String status,String ip,String userAgent,Instant occurredAt) {}
