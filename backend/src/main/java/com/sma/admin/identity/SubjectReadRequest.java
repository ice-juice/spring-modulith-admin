package com.sma.admin.identity;

/** Tenant is taken from the authenticated context. Purpose is a registered business code, never free-form user text. */
public record SubjectReadRequest(String application,long realmId,long subjectId,String purpose) {
    public SubjectReadRequest {
        if(application==null || !application.matches("[a-z][a-z0-9.-]{0,63}") || realmId<=0 || subjectId<=0
                || purpose==null || !purpose.matches("[a-z][a-z0-9._-]{0,63}"))throw new IllegalArgumentException("Invalid subject read request");
    }
}
