package com.sma.admin.common.cache;

import java.time.Instant;
import java.util.List;

/** Immutable declared deployment coverage. It does not claim every physical process was discovered. */
public record CacheNodeScope(String manifestHash,long manifestVersion,Instant capturedAt,List<Member> members) {
    public CacheNodeScope {members=List.copyOf(members);}
    public record Member(String nodeId,String processId,Long epoch,Instant leaseUntil,String stateAtCapture) {}
}
