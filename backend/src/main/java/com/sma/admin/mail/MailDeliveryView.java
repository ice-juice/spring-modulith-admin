package com.sma.admin.mail;

import java.time.Instant;
public record MailDeliveryView(Long id, String application, String scene, String recipientMasked,
                               String state, int attempts, Instant expiresAt, String lastResult, String receiptState) {
    public MailDeliveryView(Long id,String application,String scene,String recipientMasked,String state,int attempts,Instant expiresAt,String lastResult) {
        this(id,application,scene,recipientMasked,state,attempts,expiresAt,lastResult,null);
    }
}
