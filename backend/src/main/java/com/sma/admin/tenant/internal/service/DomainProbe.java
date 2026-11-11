package com.sma.admin.tenant.internal.service;

import java.time.Instant;
import java.util.List;

/** Internal domain verification result, not an activation command or a caller-supplied proof. */
public interface DomainProbe {
    Result verify(String normalizedHostname,String challenge);
    enum Failure { NONE, NOT_CONFIGURED, INVALID_DOMAIN, BUSY, DNS_UNAVAILABLE, OWNERSHIP_MISMATCH, DNS_NOT_ALLOWED, TLS_FAILED, TIMEOUT }
    record Result(boolean ownershipVerified,boolean dnsReady,boolean tlsReady,Instant tlsValidUntil,List<String> addresses,List<String> fingerprints,Failure failure) {
        public Result { addresses=List.copyOf(addresses);fingerprints=List.copyOf(fingerprints); }
        public boolean successful() { return ownershipVerified && dnsReady && tlsReady && failure==Failure.NONE && tlsValidUntil!=null
                && !addresses.isEmpty() && addresses.size()<=8 && fingerprints.size()==addresses.size() && fingerprints.stream().allMatch(value->value.matches("[a-f0-9]{64}")); }
    }
}
