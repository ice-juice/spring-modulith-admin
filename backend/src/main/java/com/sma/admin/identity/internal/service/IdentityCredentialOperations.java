package com.sma.admin.identity.internal.service;

import java.util.Arrays;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.IdentityReauthenticationStore.Action;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Internal account operations. Actor is supplied only by the authenticated application boundary. */
@Service @Transactional
public class IdentityCredentialOperations {
    public record Binding(IdentityReauthenticationStore.Use approval,String language,String address,String origin,String sourceAddress){
        @Override public String toString(){return "EmailBinding[REDACTED]";}
    }
    public record Changed(boolean changed,boolean signInRequired){}
    private final IdentityReauthenticationStore reauthentication;
    private final IdentitySessionStore sessions;
    private final IdentityAuthority authority;
    private final IdentityEmailDeliveryService deliveries;
    private final IdentityEmailChallengeStore challenges;
    private final IdentityEmailChallengeMapper challengeRows;
    private final IdentityEmailCredentialMapper credentials;
    private final IdentityExternalMapper external;
    private final IdentityEmailIndex index;
    private final IdentityPasswordWork passwords;
    private final TenantSecretCodec secrets;
    private final IdentityClock clock;
    private final JsonMapper json;

    public IdentityCredentialOperations(IdentityReauthenticationStore reauthentication,IdentitySessionStore sessions,IdentityAuthority authority,
            IdentityEmailDeliveryService deliveries,IdentityEmailChallengeStore challenges,IdentityEmailChallengeMapper challengeRows,
            IdentityEmailCredentialMapper credentials,IdentityExternalMapper external,IdentityEmailIndex index,IdentityPasswordWork passwords,
            TenantSecretCodec secrets,IdentityClock clock,JsonMapper json){
        this.reauthentication=reauthentication;this.sessions=sessions;this.authority=authority;this.deliveries=deliveries;this.challenges=challenges;
        this.challengeRows=challengeRows;this.credentials=credentials;this.external=external;this.index=index;this.passwords=passwords;this.secrets=secrets;this.clock=clock;this.json=json;
    }
    public IdentityEmailDeliveryService.Issued beginBinding(AppUserPrincipal actor,Binding input){
        IdentityAuthority.worker();if(input==null)throw invalid();requireAction(input.approval(),Action.EMAIL_BINDING);
        return reauthentication.consume(actor,input.approval(),approval->{
            String address=IdentityEmailAddress.normalize(input.address());
            if(!index.index(actor.realmId(),address).equals(approval.target()))throw invalid();
            var stamp=approval.actor();
            var issued=deliveries.create(new IdentityEmailChallengeStore.Start(stamp.loginHandle(),input.language(),IdentityEmailChallengeStore.Purpose.EMAIL_BINDING,
                    address,actor.subjectId(),stamp.subjectGeneration(),input.origin(),input.sourceAddress()));
            var row=challengeRows.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>()
                    .eq(IdentityEmailChallengeEntity::getHandleHash,IdentitySessionStore.hashToken(issued.handle())).last("FOR UPDATE"));
            if(row==null || !"PENDING".equals(row.getStatus()))throw invalid();
            row.setBindingActorJson(json.writeValueAsString(stamp));challengeRows.updateById(row);return issued;
        });
    }
    /** A new password is optional and may only add a missing method, never replace an existing password. */
    public Changed completeBinding(AppUserPrincipal actor,IdentityEmailChallengeStore.Use use,String newPassword){
        IdentityAuthority.worker();if(use==null || use.purpose()!=IdentityEmailChallengeStore.Purpose.EMAIL_BINDING)throw invalid();
        return challenges.consume(use,grant->{
            var current=sessions.currentActor(actor);
            var challenge=challengeRows.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().eq(IdentityEmailChallengeEntity::getId,grant.challengeId()).last("FOR UPDATE"));
            if(challenge==null || challenge.getBindingActorJson()==null || grant.subjectId()==null || grant.subjectId()!=actor.subjectId()
                    || !grant.authority().equals(current.authority()) || !current.equals(json.readValue(challenge.getBindingActorJson(),IdentitySessionStore.ActorStamp.class)))throw invalid();
            var row=credential(actor.subjectId());
            var occupied=credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getRealmId,actor.realmId())
                    .eq(IdentityEmailCredentialEntity::getEmailIndex,grant.targetIndex()).last("FOR UPDATE"));
            if(occupied!=null && occupied.getSubjectId()!=actor.subjectId())throw new BizException(IdentityErrorCode.IDENTITY_LINK_CONFLICT);
            if(row!=null && row.getPasswordHash()!=null && newPassword!=null)throw new BizException(IdentityErrorCode.PASSWORD_ALREADY_CONFIGURED);
            if(row!=null && row.getEmailIndex().equals(grant.targetIndex()) && newPassword==null)return new Changed(false,false);
            String hash=newPassword==null?null:passwords.encode(grant.address(),newPassword);
            boolean insert=row==null;
            if(insert){
                row=new IdentityEmailCredentialEntity();row.setId(IdWorker.getId());row.setTenantId(actor.tenantId());row.setRealmId(actor.realmId());row.setSubjectId(actor.subjectId());
                row.setEmailVersion(1L);row.setPasswordVersion(1L);
            }else{
                row.setEmailVersion(Math.addExact(row.getEmailVersion(),1));
                if(hash!=null)row.setPasswordVersion(Math.addExact(row.getPasswordVersion(),1));
            }
            row.setEmailIndex(grant.targetIndex());row.setEmailVerifiedAt(clock.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
            if(hash!=null)row.setPasswordHash(hash);
            char[] address=grant.address().toCharArray();
            try{row.setEmailEnvelope(secrets.encrypt(IdentityEmailAccountService.reference(row),"verified-email-address",address));}finally{Arrays.fill(address,'\0');}
            try{if(insert)credentials.insert(row);else credentials.updateById(row);}catch(DuplicateKeyException conflict){throw new BizException(IdentityErrorCode.IDENTITY_LINK_CONFLICT);}
            sessions.revokeSubject(actor.subjectId());return new Changed(true,true);
        });
    }
    public Changed removePassword(AppUserPrincipal actor,IdentityReauthenticationStore.Use approval){
        IdentityAuthority.worker();requireAction(approval,Action.PASSWORD_REMOVAL);
        return reauthentication.consume(actor,approval,verified->{
            var row=credential(actor.subjectId());if(row==null || row.getPasswordHash()==null)throw invalid();
            var live=authority.match(verified.actor().authority());
            if(!live.configuration().getGoogleEnabled() || googleLinks(actor.subjectId(),null)==0)throw lastMethod();
            row.setPasswordHash(null);row.setPasswordVersion(Math.addExact(row.getPasswordVersion(),1));credentials.updateById(row);
            sessions.revokeSubject(actor.subjectId());return new Changed(true,true);
        });
    }
    public Changed removeGoogle(AppUserPrincipal actor,IdentityReauthenticationStore.Use approval){
        IdentityAuthority.worker();requireAction(approval,Action.GOOGLE_REMOVAL);
        return reauthentication.consume(actor,approval,verified->{
            long id=Long.parseLong(verified.target());var row=external.selectOne(new LambdaQueryWrapper<IdentityExternalEntity>().eq(IdentityExternalEntity::getId,id).last("FOR UPDATE"));
            if(row==null || row.getSubjectId()!=actor.subjectId() || row.getRealmId()!=actor.realmId() || !"https://accounts.google.com".equals(row.getIssuer()))throw invalid();
            var live=authority.match(verified.actor().authority());var password=credential(actor.subjectId());
            boolean passwordAvailable=live.realm().getPasswordEnabled() && password!=null && password.getPasswordHash()!=null && password.getEmailVerifiedAt()!=null;
            boolean googleAvailable=live.configuration().getGoogleEnabled() && googleLinks(actor.subjectId(),id)>0;
            if(!passwordAvailable && !googleAvailable)throw lastMethod();
            external.deleteById(id);sessions.revokeSubject(actor.subjectId());return new Changed(true,true);
        });
    }
    private IdentityEmailCredentialEntity credential(long subject){
        return credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getSubjectId,subject).last("FOR UPDATE"));
    }
    private int googleLinks(long subject,Long excluding){
        var query=new LambdaQueryWrapper<IdentityExternalEntity>().select(IdentityExternalEntity::getId).eq(IdentityExternalEntity::getSubjectId,subject)
                .eq(IdentityExternalEntity::getIssuer,"https://accounts.google.com");
        if(excluding!=null)query.ne(IdentityExternalEntity::getId,excluding);
        return external.selectList(query.last("LIMIT 1 FOR SHARE")).size();
    }
    private void requireAction(IdentityReauthenticationStore.Use input,Action expected){if(input==null || input.action()!=expected)throw invalid();}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);}
    private BizException lastMethod(){return new BizException(IdentityErrorCode.LAST_AUTHENTICATION_METHOD);}
}
