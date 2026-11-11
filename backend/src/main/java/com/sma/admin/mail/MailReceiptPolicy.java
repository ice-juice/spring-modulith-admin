package com.sma.admin.mail;

import com.sma.admin.common.security.ActorRef;

/** Bind authenticated gateway identity to exact tenant/profile/provider/application. No implementation means deny. */
public interface MailReceiptPolicy {
    boolean canAccept(ActorRef gateway, MailReceiptApi.Command receipt);
}
