package com.sma.admin.identity;

/** Minimal identity event. Business profiles remain owned by their business module; consumers use subjectId idempotently. */
public record ApplicationSubjectCreated(long tenantId,long realmId,long clientId,String application,long subjectId,String method) {}
