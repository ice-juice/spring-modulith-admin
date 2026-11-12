package com.sma.admin.defense;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sma.admin.common.security.SecuritySignal;
/** Read-only, current authorization on every call; no cross-tenant parameter. */
public interface SecurityEvents {
    record View(UUID eventId,UUID correlationId,Instant occurredAt,Instant receivedAt,Instant expiresAt,
            String fact,String category,String severity,String outcome,String assessment,int ruleVersion,
            SecuritySignal signal,boolean held) {}
    List<View> tenant(int limit);
    Optional<View> tenant(UUID eventId);
    List<View> platform(int limit);
    Optional<View> platform(UUID eventId);
}
