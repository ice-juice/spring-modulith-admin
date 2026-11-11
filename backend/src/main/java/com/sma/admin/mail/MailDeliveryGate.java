package com.sma.admin.mail;
import java.time.Instant;

/** Join mail's short work transaction with non-locking reads. No domain locks, independent transaction, writes or external I/O. */
public interface MailDeliveryGate {
    String owner();
    boolean mayDeliver(Delivery delivery);
    record Delivery(long tenantId,long deliveryId,String application,String scene,String reference,Instant expiresAt) {}
}
