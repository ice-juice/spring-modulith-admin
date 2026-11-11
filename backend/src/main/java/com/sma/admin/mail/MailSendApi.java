package com.sma.admin.mail;

import java.time.Instant;
import java.util.Map;

/** Enqueue in the caller's database transaction. No synchronous external mail side effect. */
public interface MailSendApi {
    MailDeliveryView enqueue(Command command);
    MailDeliveryView cancel(long deliveryId);
    /** Non-secret reference to an owning module's persisted validity state; missing handlers fail closed. */
    record Guard(String owner,String reference) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    record Command(String application, String scene, String templateCode, String language, String recipient,
                   Map<String,String> variables, Instant expiresAt, String idempotencyKey,Guard guard) {
        public Command(String application,String scene,String templateCode,String language,String recipient,Map<String,String> variables,Instant expiresAt,String idempotencyKey){
            this(application,scene,templateCode,language,recipient,variables,expiresAt,idempotencyKey,null);
        }
        public Command { variables = variables == null ? Map.of() : Map.copyOf(variables); }
        @Override public String toString() { return "MailCommand[REDACTED]"; }
    }
}
