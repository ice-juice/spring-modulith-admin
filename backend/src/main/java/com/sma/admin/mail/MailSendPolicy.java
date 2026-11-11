package com.sma.admin.mail;

import com.sma.admin.common.security.ActorRef;

/** The owning business module must verify recipient/object ownership, not just compare an application string. */
public interface MailSendPolicy {
    String application();
    boolean canSend(ActorRef actor, MailSendApi.Command command);
}
