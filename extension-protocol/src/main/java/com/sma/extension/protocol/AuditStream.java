package com.sma.extension.protocol;

import java.time.Instant;
import java.util.List;

/** Public non-secret audit mirror protocol; canonical Entry encoding matches the authoritative audit chain. */
public final class AuditStream {
    private AuditStream() {}
    public record Entry(long sequence,Instant occurredAt,String actor,String action,String resource,String outcome,String detailHash,String previousHash) {}
    public record Hashed(Entry entry,String entryHash) {}
    public record Batch(long anchorSequence,String anchorHash,List<Hashed> entries,long headSequence) {public Batch {entries=List.copyOf(entries);}}
    public static String verify(long previousSequence,String previousHash,Hashed current) {
        Checks.hash(previousHash);Checks.hash(current.entryHash());var entry=current.entry();
        Checks.require(entry.sequence()==previousSequence+1 && previousHash.equals(entry.previousHash()) && current.entryHash().equals(Hashes.sha256(ProtocolJson.write(entry))),"audit-chain-mismatch");return current.entryHash();
    }
}
