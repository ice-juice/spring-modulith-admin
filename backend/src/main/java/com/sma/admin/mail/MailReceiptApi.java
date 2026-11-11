package com.sma.admin.mail;

import java.time.Instant;

/** Trusted provider gateway only: authenticate vendor callbacks before forwarding a signed OpenAPI request. */
public interface MailReceiptApi {
    Result accept(Command command);
    enum Type { DELIVERED, BOUNCED, COMPLAINT }
    record Command(String application, long profileId, String provider, String eventId,
                   String messageId, Type type, Instant occurredAt) {}
    record Result(long deliveryId, String receiptState, boolean duplicate) {}
}
