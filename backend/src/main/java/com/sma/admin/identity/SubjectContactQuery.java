package com.sma.admin.identity;

import java.time.Instant;
import java.util.Optional;

/** Contact access is separately authorized. A provider email claim alone is not a verified application contact. */
public interface SubjectContactQuery {
    record VerifiedEmail(SubjectQuery.Subject subject,String email,Instant verifiedAt){
        @Override public String toString(){return "VerifiedEmail[REDACTED]";}
    }
    Optional<VerifiedEmail> verifiedEmail(SubjectReadRequest request);
}
