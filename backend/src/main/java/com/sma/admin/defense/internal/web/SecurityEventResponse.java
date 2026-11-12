package com.sma.admin.defense.internal.web;

import java.time.Instant;
import java.util.UUID;
import com.sma.admin.common.security.RequestSource;
import com.sma.admin.common.security.SecuritySignal;
import com.sma.admin.defense.SecurityEvents;

/** Fixed authorized evidence projection. Never exposes persistence payload, digest, capacity or secret material. */
public record SecurityEventResponse(UUID eventId,UUID correlationId,Instant occurredAt,Instant receivedAt,Instant expiresAt,
        String fact,String category,String severity,String outcome,String assessment,int ruleVersion,
        SecuritySignal.Attribution attribution,RequestSource source,boolean held,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) SecuritySignal.Metadata metadata) {
    static SecurityEventResponse from(SecurityEvents.View view,boolean includeMetadata){
        return new SecurityEventResponse(view.eventId(),view.correlationId(),view.occurredAt(),view.receivedAt(),view.expiresAt(),
                view.fact(),view.category(),view.severity(),view.outcome(),view.assessment(),view.ruleVersion(),
                view.signal().attribution(),view.signal().source(),view.held(),includeMetadata?view.signal().metadata():null);
    }
}
