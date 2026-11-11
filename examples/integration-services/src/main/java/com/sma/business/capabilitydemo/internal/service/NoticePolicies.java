package com.sma.business.capabilitydemo.internal.service;

import com.sma.admin.common.security.ActorRef;
import com.sma.admin.identity.*;
import com.sma.admin.mail.*;
import org.springframework.stereotype.Component;

@Component
public class NoticePolicies implements SubjectAccessPolicy,MailSendPolicy {
    public static final String APPLICATION="capabilitydemo",SCENE="account-notice";
    private final NoticeAuthorization authorization;
    public NoticePolicies(NoticeAuthorization authorization){this.authorization=authorization;}
    @Override public String application(){return APPLICATION;}
    @Override public boolean canRead(ActorRef actor,SubjectReadRequest request,Content content){
        return APPLICATION.equals(request.application()) && SCENE.equals(request.purpose())
                && content==Content.VERIFIED_EMAIL && authorization.mayRead(actor,request,content);
    }
    @Override public boolean canSend(ActorRef actor,MailSendApi.Command command){
        return APPLICATION.equals(command.application()) && SCENE.equals(command.scene()) && SCENE.equals(command.templateCode())
                && authorization.mayEnqueue(actor,command);
    }
}
