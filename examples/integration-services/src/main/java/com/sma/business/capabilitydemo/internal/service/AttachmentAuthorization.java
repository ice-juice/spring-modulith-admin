package com.sma.business.capabilitydemo.internal.service;

import com.sma.admin.common.security.ActorRef;

/** Business implementation must check a real document and the original actor's permission to attach to it. */
public interface AttachmentAuthorization {
    boolean mayUpload(ActorRef actor,String documentId);
}
