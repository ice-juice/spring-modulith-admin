package com.sma.admin.identity.internal.service;

import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.*;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account operations own grant consumption and credential/session mutation in one resource transaction. */
@Service
public class IdentityEmailAccountService {
    public record Challenge(String loginHandle,String language,IdentityEmailChallengeStore.Purpose purpose,String address,String origin,String sourceAddress){
        @Override public String toString(){return "EmailAccountChallenge[REDACTED]";}
    }
    private final IdentityEmailChallengeStore challenges;private final IdentityEmailDeliveryService deliveries;private final IdentityEmailBudget budgets;
    private final IdentityAuthority authority;private final IdentityEmailIndex index;private final IdentityEmailCredentialMapper credentials;private final IdentitySubjectMapper subjects;
    private final IdentitySessionStore sessions;private final IdentityPasswordWork passwords;private final TenantSecretCodec secrets;private final IdentityClock clock;private final ApplicationEventPublisher events;
    public IdentityEmailAccountService(IdentityEmailChallengeStore challenges,IdentityEmailDeliveryService deliveries,IdentityEmailBudget budgets,IdentityAuthority authority,
            IdentityEmailIndex index,IdentityEmailCredentialMapper credentials,IdentitySubjectMapper subjects,IdentitySessionStore sessions,IdentityPasswordWork passwords,
            TenantSecretCodec secrets,IdentityClock clock,ApplicationEventPublisher events){
        this.challenges=challenges;this.deliveries=deliveries;this.budgets=budgets;this.authority=authority;this.index=index;this.credentials=credentials;this.subjects=subjects;
        this.sessions=sessions;this.passwords=passwords;this.secrets=secrets;this.clock=clock;this.events=events;
    }
    /** Both existing and unknown addresses receive the same challenge shape. A reset never creates a password credential. */
    @Transactional public IdentityEmailDeliveryService.Issued start(Challenge input){
        IdentityAuthority.worker();if(input==null || input.purpose()==null || input.purpose()==IdentityEmailChallengeStore.Purpose.EMAIL_BINDING)throw invalid();
        budgets.lock();var snapshot=authority.capture(input.loginHandle());String address=IdentityEmailAddress.normalize(input.address());
        Long subjectId=null,generation=null;
        if(input.purpose()==IdentityEmailChallengeStore.Purpose.PASSWORD_RESET){
            String target=index.index(snapshot.realmId(),address);var credential=byAddress(snapshot.realmId(),target);
            if(credential!=null && credential.getPasswordHash()!=null && credential.getEmailVerifiedAt()!=null){
                var subject=subjects.selectOne(new LambdaQueryWrapper<IdentitySubjectEntity>().eq(IdentitySubjectEntity::getId,credential.getSubjectId()).last("FOR SHARE"));
                if(subject!=null && subject.getEnabled() && subject.getRealmId()==snapshot.realmId()){subjectId=subject.getId();generation=subject.getSecurityVersion();}
            }
        }
        return deliveries.create(new IdentityEmailChallengeStore.Start(input.loginHandle(),input.language(),input.purpose(),address,subjectId,generation,input.origin(),input.sourceAddress()));
    }
    @Transactional public IdentitySessionStore.Issued register(IdentityEmailChallengeStore.Use use,String password){
        IdentityAuthority.worker();if(use==null || use.purpose()!=IdentityEmailChallengeStore.Purpose.REGISTRATION)throw invalid();
        return challenges.consume(use,grant->{
            var snapshot=grant.authority();if(grant.subjectId()!=null || byAddress(snapshot.realmId(),grant.targetIndex())!=null)throw new BizException(IdentityErrorCode.EMAIL_ACCOUNT_CONFLICT);
            String hash=passwords.encode(grant.address(),password);
            var subject=new IdentitySubjectEntity();subject.setRealmId(snapshot.realmId());subject.setEnabled(true);subject.setSecurityVersion(1L);subjects.insert(subject);
            var row=new IdentityEmailCredentialEntity();row.setId(IdWorker.getId());row.setTenantId(snapshot.tenantId());row.setRealmId(snapshot.realmId());row.setSubjectId(subject.getId());
            row.setEmailIndex(grant.targetIndex());row.setEmailVersion(1L);row.setEmailVerifiedAt(clock.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));row.setPasswordHash(hash);row.setPasswordVersion(1L);
            char[] address=grant.address().toCharArray();try{row.setEmailEnvelope(secrets.encrypt(reference(row),"verified-email-address",address));}finally{Arrays.fill(address,'\0');}
            try{credentials.insert(row);}catch(DuplicateKeyException conflict){throw new BizException(IdentityErrorCode.EMAIL_ACCOUNT_CONFLICT);}
            events.publishEvent(new ApplicationSubjectCreated(snapshot.tenantId(),snapshot.realmId(),snapshot.clientId(),snapshot.application(),subject.getId(),"EMAIL"));
            return sessions.verifiedSubject(snapshot,subject.getId(),IdentitySessionStore.AuthenticationMethod.EMAIL_VERIFIED);
        });
    }
    @Transactional public void resetPassword(IdentityEmailChallengeStore.Use use,String password){
        IdentityAuthority.worker();if(use==null || use.purpose()!=IdentityEmailChallengeStore.Purpose.PASSWORD_RESET)throw invalid();
        challenges.consume(use,grant->{
            if(grant.subjectId()==null)throw invalid();
            var subject=subjects.selectOne(new LambdaQueryWrapper<IdentitySubjectEntity>().eq(IdentitySubjectEntity::getId,grant.subjectId()).last("FOR UPDATE"));
            if(subject==null || !subject.getEnabled() || subject.getRealmId()!=grant.authority().realmId() || !subject.getSecurityVersion().equals(grant.subjectGeneration()))throw invalid();
            var row=credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getSubjectId,subject.getId()).last("FOR UPDATE"));
            if(row==null || row.getRealmId()!=grant.authority().realmId() || !row.getEmailIndex().equals(grant.targetIndex()) || row.getPasswordHash()==null || row.getEmailVerifiedAt()==null)throw invalid();
            row.setPasswordHash(passwords.encode(grant.address(),password));row.setPasswordVersion(Math.addExact(row.getPasswordVersion(),1));credentials.updateById(row);
            sessions.revokeSubject(subject.getId());return null;
        });
    }
    private IdentityEmailCredentialEntity byAddress(long realm,String target){
        return credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getRealmId,realm)
                .eq(IdentityEmailCredentialEntity::getEmailIndex,target).last("FOR UPDATE"));
    }
    static TenantConfigurationRef reference(IdentityEmailCredentialEntity row){return new TenantConfigurationRef(row.getTenantId(),"identity",row.getId(),row.getEmailVersion(),1);}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);}
}
