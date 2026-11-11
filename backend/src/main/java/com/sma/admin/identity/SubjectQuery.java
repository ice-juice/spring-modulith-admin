package com.sma.admin.identity;

import java.util.Optional;

/** Stable read contract for static business modules. Does not authenticate, issue sessions or grant business permissions. */
public interface SubjectQuery {
    record Subject(long tenantId,long realmId,long subjectId,String application,boolean enabled){}
    /** Current application user only; validates the current session and exact audience. */
    Subject current(String application);
    /** Requires the application's explicit SubjectAccessPolicy, including for administrative and job callers. */
    Optional<Subject> find(SubjectReadRequest request);
}
