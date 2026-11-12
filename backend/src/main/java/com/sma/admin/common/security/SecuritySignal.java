package com.sma.admin.common.security;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable observation envelope, not an authorization credential or proof of persistence. No free-form evidence. */
public record SecuritySignal(UUID eventId,UUID correlationId,Instant occurredAt,Fact fact,Attribution attribution,RequestSource source,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Metadata metadata) {
    /** Retains the original binary constructor and the exact absent-metadata JSON shape. */
    public SecuritySignal(UUID eventId,UUID correlationId,Instant occurredAt,Fact fact,Attribution attribution,RequestSource source){
        this(eventId,correlationId,occurredAt,fact,attribution,source,null);
    }
    public record Observer(String deployment,String nodeId,UUID processId) {
        public Observer {
            if(deployment==null||!deployment.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")
                    ||nodeId==null||!nodeId.matches("[a-z][a-z0-9-]{0,47}")||processId==null)
                throw new IllegalArgumentException("Invalid signal observer");
        }
    }
    public enum Method { GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE, CONNECT, OTHER }
    public enum Route { UNRESOLVED, MATCHED, UNMAPPED }
    /** Status is the response decision at observation, not proof of transport completion. */
    public record Http(Method method,Route route,String template,Integer status) {
        public Http {
            Objects.requireNonNull(method);Objects.requireNonNull(route);
            if(status!=null&&(status<100||status>599))throw new IllegalArgumentException("Invalid observed response status");
            if(route==Route.MATCHED){
                if(template==null||template.length()>256||!template.startsWith("/")
                        ||template.chars().anyMatch(c->c<33||c>126)||template.indexOf('?')>=0||template.indexOf('#')>=0)
                    throw new IllegalArgumentException("Invalid declared route template");
            }else if(template!=null)throw new IllegalArgumentException("Unresolved routes cannot carry a template");
        }
    }
    public record Metadata(Observer observer,Http http) { public Metadata { Objects.requireNonNull(observer); } }
    public enum Scope { PLATFORM, TENANT }
    public enum Identity { ANONYMOUS, UNRECOGNIZED, ADMIN_USER, APP_USER, APPLICATION, SYSTEM_JOB }
    public enum Category { AUTHENTICATION, AUTHORIZATION, REQUEST_POLICY, CAPACITY, TENANT_ENTRY, SIGNATURE_REPLAY, FILE_INSPECTION, HONEYPOT }
    public enum Severity { LOW, MEDIUM, HIGH }
    public enum Outcome { REJECTED, OBSERVED }
    public enum Assessment { NO_ATTACK_INFERENCE, SUSPICIOUS_ACTIVITY }
    public enum Fact {
        AUTHENTICATION_REJECTED(Category.AUTHENTICATION,Severity.LOW,Outcome.REJECTED,Assessment.NO_ATTACK_INFERENCE),
        AUTHORIZATION_REJECTED(Category.AUTHORIZATION,Severity.LOW,Outcome.REJECTED,Assessment.NO_ATTACK_INFERENCE),
        BROWSER_ORIGIN_REJECTED(Category.REQUEST_POLICY,Severity.LOW,Outcome.REJECTED,Assessment.NO_ATTACK_INFERENCE),
        REQUEST_INPUT_REJECTED(Category.REQUEST_POLICY,Severity.LOW,Outcome.REJECTED,Assessment.NO_ATTACK_INFERENCE),
        UNMAPPED_REQUEST_OBSERVED(Category.REQUEST_POLICY,Severity.LOW,Outcome.OBSERVED,Assessment.NO_ATTACK_INFERENCE),
        REQUEST_BUDGET_REJECTED(Category.CAPACITY,Severity.LOW,Outcome.REJECTED,Assessment.NO_ATTACK_INFERENCE),
        TENANT_ENTRY_REJECTED(Category.TENANT_ENTRY,Severity.LOW,Outcome.REJECTED,Assessment.NO_ATTACK_INFERENCE),
        SIGNATURE_REPLAY_REJECTED(Category.SIGNATURE_REPLAY,Severity.MEDIUM,Outcome.REJECTED,Assessment.SUSPICIOUS_ACTIVITY),
        FILE_INSPECTION_REJECTED(Category.FILE_INSPECTION,Severity.MEDIUM,Outcome.REJECTED,Assessment.NO_ATTACK_INFERENCE),
        HONEYPOT_OBSERVED(Category.HONEYPOT,Severity.MEDIUM,Outcome.OBSERVED,Assessment.SUSPICIOUS_ACTIVITY);
        private final Category category;private final Severity severity;private final Outcome outcome;private final Assessment assessment;
        Fact(Category category,Severity severity,Outcome outcome,Assessment assessment){this.category=category;this.severity=severity;this.outcome=outcome;this.assessment=assessment;}
        public Category category(){return category;}public Severity severity(){return severity;}public Outcome outcome(){return outcome;}
        public Assessment assessment(){return assessment;}public int ruleVersion(){return 1;}
    }
    public record Attribution(Scope scope,Identity identity,Long tenantId,String subject,String portal,String audience,Long realmId,Long clientId) {
        public Attribution {
            Objects.requireNonNull(scope);Objects.requireNonNull(identity);
            if(scope==Scope.PLATFORM){
                if(tenantId!=null || subject!=null || portal!=null || audience!=null || realmId!=null || clientId!=null
                        || identity!=Identity.ANONYMOUS && identity!=Identity.UNRECOGNIZED)throw invalid();
            }else{
                if(tenantId==null || tenantId<=0)throw invalid();
                switch(identity){
                    case ADMIN_USER->{if(!id(subject) || !portal(portal) || audience!=null || realmId!=null || clientId!=null)throw invalid();}
                    case APPLICATION->{if(subject==null || !subject.matches("[A-Za-z0-9_-]{1,64}") || !portal(portal) || audience!=null || realmId!=null || clientId!=null)throw invalid();}
                    case APP_USER->{if(!id(subject) || portal!=null || audience==null || !audience.matches("[a-z][a-z0-9.-]{0,63}")
                            || realmId==null || realmId<=0 || clientId==null || clientId<=0)throw invalid();}
                    case SYSTEM_JOB->{if(subject!=null || portal!=null || audience!=null || realmId!=null || clientId!=null)throw invalid();}
                    default->throw invalid();
                }
            }
        }
        private static boolean id(String value){if(value==null || !value.matches("[1-9][0-9]{0,18}"))return false;try{return Long.parseLong(value)>0;}catch(NumberFormatException invalid){return false;}}
        private static boolean portal(String value){return value!=null && value.matches("[a-z][a-z0-9-]{0,31}");}
        private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid signal attribution");}
    }
    public SecuritySignal {
        Objects.requireNonNull(eventId);Objects.requireNonNull(correlationId);Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(fact);Objects.requireNonNull(attribution);
    }
}
