package com.sma.admin.auth;
import java.time.Instant;
public record LoginEvent(long tenantId,Long userId,String username,String portal,String status,String ip,String userAgent,Instant occurredAt) {}
