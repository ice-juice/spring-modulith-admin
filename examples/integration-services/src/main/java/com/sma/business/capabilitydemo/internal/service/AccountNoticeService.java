package com.sma.business.capabilitydemo.internal.service;

import java.time.Instant;
import java.util.*;
import com.sma.admin.identity.*;
import com.sma.admin.mail.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Teaching adapter: callers own their notice state, authorization implementation, delivery reference and reconciliation. */
@Service
public class AccountNoticeService {
    private final SubjectQuery subjects;private final SubjectContactQuery contacts;private final MailSendApi mail;
    public AccountNoticeService(SubjectQuery subjects,SubjectContactQuery contacts,MailSendApi mail){this.subjects=subjects;this.contacts=contacts;this.mail=mail;}
    public SubjectQuery.Subject currentSubject(){return subjects.current(NoticePolicies.APPLICATION);}
    @Transactional
    public Optional<MailDeliveryView> enqueueNewNotice(long realmId,long subjectId,String persistedNoticeId,String language,Instant persistedExpiry){
        if(persistedNoticeId==null || !persistedNoticeId.matches("[A-Za-z0-9_-]{1,64}") || persistedExpiry==null)throw new IllegalArgumentException("Persisted notice identity required");
        var request=new SubjectReadRequest(NoticePolicies.APPLICATION,realmId,subjectId,NoticePolicies.SCENE);
        return contacts.verifiedEmail(request).map(contact->mail.enqueue(new MailSendApi.Command(NoticePolicies.APPLICATION,NoticePolicies.SCENE,
                NoticePolicies.SCENE,language,contact.email(),Map.of("noticeId",persistedNoticeId),persistedExpiry,"capabilitydemo-notice:"+persistedNoticeId)));
    }
}
