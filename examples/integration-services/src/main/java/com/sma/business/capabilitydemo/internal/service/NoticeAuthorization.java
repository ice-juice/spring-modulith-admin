package com.sma.business.capabilitydemo.internal.service;

import com.sma.admin.common.security.ActorRef;
import com.sma.admin.identity.SubjectAccessPolicy;
import com.sma.admin.identity.SubjectReadRequest;
import com.sma.admin.mail.MailSendApi;

/** Implement against this business's owned member/notice records; there is intentionally no allow-all implementation. */
public interface NoticeAuthorization {
    boolean mayRead(ActorRef actor,SubjectReadRequest request,SubjectAccessPolicy.Content content);
    boolean mayEnqueue(ActorRef actor,MailSendApi.Command command);
}
