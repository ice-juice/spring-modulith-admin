package com.sma.admin.identity.internal.service;

import java.time.Instant;
import java.util.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.mail.MailSendApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal transaction coordinator. Public account flows must authorize the purpose and subject before calling. */
@Service
public class IdentityEmailDeliveryService {
    public record Issued(String handle,String proof,Instant expiresAt){@Override public String toString(){return "EmailChallengeIssued[REDACTED]";}}
    private final IdentityEmailChallengeStore challenges;private final MailSendApi mail;private final IdentityEmailProperties settings;
    public IdentityEmailDeliveryService(IdentityEmailChallengeStore challenges,MailSendApi mail,IdentityEmailProperties settings){this.challenges=challenges;this.mail=mail;this.settings=settings;}
    @Transactional public Issued create(IdentityEmailChallengeStore.Start input){
        IdentityAuthority.worker();long tenant=ActorContext.tenantId();
        try(var prepared=challenges.begin(input)){
            char[] code=prepared.code().copy();
            try{
                String reference=Long.toString(prepared.challengeId()),work="identity-email:"+reference;
                var command=new MailSendApi.Command("identity",prepared.scene(),prepared.templateCode(),prepared.language(),prepared.address(),
                        Map.of("code",new String(code),"minutes",Long.toString((settings.lifetime().toMillis()+59999)/60000)),prepared.expiresAt(),work,
                        new MailSendApi.Guard(IdentityEmailMailPolicy.OWNER,reference));
                var delivery=ActorContext.withinJob(tenant,work,()->mail.enqueue(command));
                challenges.attachDelivery(prepared.challengeId(),delivery.id());
                return new Issued(prepared.handle(),prepared.proof(),prepared.expiresAt());
            }finally{Arrays.fill(code,'\0');}
        }
    }
}
