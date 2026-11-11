package com.sma.admin.identity;

import com.sma.admin.common.security.ActorRef;

/** Implement in the owning business module. Check real object ownership and purpose; an application string is not authorization. */
public interface SubjectAccessPolicy {
    enum Content { BASIC, VERIFIED_EMAIL }
    String application();
    /** Read-only, bounded authorization; do not recurse into identity queries, make network calls or acquire domain write locks. */
    boolean canRead(ActorRef actor,SubjectReadRequest request,Content content);
}
