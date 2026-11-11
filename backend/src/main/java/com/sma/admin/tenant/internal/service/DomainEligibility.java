package com.sma.admin.tenant.internal.service;

import java.time.Instant;
import java.util.Set;
import com.sma.admin.tenant.internal.persistence.DomainEntity;

/** One shared entry/activation policy. Network check completion alone never changes status to ACTIVE. */
public final class DomainEligibility {
    private DomainEligibility() {}
    public static boolean local(DomainEntity row) {
        return "DEPLOYMENT".equals(row.getProvenance()) && "LOCAL".equals(row.getTlsStatus())
                && Set.of("localhost","127.0.0.1","[::1]").contains(row.getHostname());
    }
    public static boolean verified(DomainEntity row,Instant now) {
        if(!"VERIFIED".equals(row.getVerificationStatus())) return false;
        return local(row) || "READY".equals(row.getDnsStatus()) && "READY".equals(row.getTlsStatus())
                && row.getTlsValidUntil()!=null && row.getTlsValidUntil().isAfter(now);
    }
    public static boolean activatable(DomainEntity row,Instant now) {
        return verified(row,now) && (local(row) || row.getVerifiedAt()!=null && row.getVerifiedAt().isAfter(now.minusSeconds(86400))
                && row.getChallengeToken()!=null && DomainDigests.hash(row.getChallengeToken()).equals(row.getVerifiedChallengeHash()));
    }
}
