package com.sma.admin.common.request;

/** Exactly one consumer owns each event type. Delivery can repeat after an unknown result. */
public interface BusinessOutboxConsumer {
    record Delivery(long id, long tenantId, String resourceId, long resourceRevision,
                 String eventType, String keyHash, byte[] payload) {
        public Delivery { payload = payload.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
    }
    boolean supports(String eventType);
    void deliver(Delivery event);
}
