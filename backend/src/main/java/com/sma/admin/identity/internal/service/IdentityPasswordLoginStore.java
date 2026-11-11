package com.sma.admin.identity.internal.service;

import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Rate charges commit before CPU verification. Finish re-reads every identity/credential generation before issuing. */
@Service
public class IdentityPasswordLoginStore {
    public record Attempt(String loginHandle,String address,String origin,String sourceAddress){@Override public String toString(){return "PasswordAttempt[REDACTED]";}}
    public record Prepared(IdentityAuthority.Snapshot authority,Long credentialId,Long subjectId,Long subjectGeneration,String emailIndex,
            Long emailVersion,Long passwordVersion,String passwordHash,Instant expiresAt,IdentityErrorCode error){
        public boolean found(){return credentialId!=null;}
        @Override public String toString(){return "PreparedPasswordLogin[REDACTED]";}
    }
    private final IdentityEmailBudget budgets;private final IdentityAuthority authority;private final IdentityEmailIndex index;
    private final IdentityEmailCredentialMapper credentials;private final IdentitySubjectMapper subjects;private final IdentitySessionStore sessions;
    private final IdentityPasswordWork.Settings settings;private final IdentityClock clock;private final JsonMapper json;
    public IdentityPasswordLoginStore(IdentityEmailBudget budgets,IdentityAuthority authority,IdentityEmailIndex index,IdentityEmailCredentialMapper credentials,
            IdentitySubjectMapper subjects,IdentitySessionStore sessions,IdentityPasswordWork.Settings settings,IdentityClock clock,JsonMapper json){
        this.budgets=budgets;this.authority=authority;this.index=index;this.credentials=credentials;this.subjects=subjects;this.sessions=sessions;this.settings=settings;this.clock=clock;this.json=json;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Prepared prepare(Attempt input){
        IdentityAuthority.worker();if(input==null || input.loginHandle()==null || !input.loginHandle().matches("[A-Za-z0-9_-]{43}"))throw invalid();
        String address=IdentityEmailAddress.normalize(input.address());IdentityEmailBudget.address(input.sourceAddress());var budget=budgets.lock();
        if(!budgets.verification(budget,input.loginHandle(),input.sourceAddress()))return limited();
        var snapshot=authority.capture(input.loginHandle());var live=authority.match(snapshot);
        if(!live.realm().getPasswordEnabled() || snapshot.platform().equals("WEB") && (input.origin()==null || !json.readValue(live.configuration().getOriginsJson(),new TypeReference<List<String>>(){}).contains(input.origin()))
                || !snapshot.platform().equals("WEB") && input.origin()!=null)throw invalid();
        String target=index.index(snapshot.realmId(),address);
        if(!budgets.passwordTarget(snapshot.realmId(),target,settings.perTargetPerMinute()))return limited();
        var credential=credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getRealmId,snapshot.realmId())
                .eq(IdentityEmailCredentialEntity::getEmailIndex,target).last("FOR SHARE"));
        var subject=credential==null?null:subjects.selectOne(new LambdaQueryWrapper<IdentitySubjectEntity>().eq(IdentitySubjectEntity::getId,credential.getSubjectId()).last("FOR SHARE"));
        Instant expires=clock.now().plusSeconds(30);
        if(credential==null || credential.getPasswordHash()==null || credential.getEmailVerifiedAt()==null || subject==null || !subject.getEnabled() || subject.getRealmId()!=snapshot.realmId())
            return new Prepared(snapshot,null,null,null,target,null,null,null,expires,null);
        return new Prepared(snapshot,credential.getId(),subject.getId(),subject.getSecurityVersion(),target,credential.getEmailVersion(),credential.getPasswordVersion(),credential.getPasswordHash(),expires,null);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public IdentitySessionStore.Issued finish(Prepared prepared){
        IdentityAuthority.worker();if(prepared==null || prepared.error()!=null || !prepared.found() || prepared.passwordHash()==null || prepared.expiresAt()==null || !prepared.expiresAt().isAfter(clock.now()))throw invalid();
        var live=authority.match(prepared.authority());if(!live.realm().getPasswordEnabled())throw invalid();
        var subject=subjects.selectOne(new LambdaQueryWrapper<IdentitySubjectEntity>().eq(IdentitySubjectEntity::getId,prepared.subjectId()).last("FOR UPDATE"));
        var credential=credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getId,prepared.credentialId()).last("FOR UPDATE"));
        if(subject==null || !subject.getEnabled() || subject.getRealmId()!=prepared.authority().realmId() || !subject.getSecurityVersion().equals(prepared.subjectGeneration())
                || credential==null || !credential.getSubjectId().equals(subject.getId()) || credential.getRealmId()!=prepared.authority().realmId()
                || !credential.getEmailIndex().equals(prepared.emailIndex()) || !credential.getEmailVersion().equals(prepared.emailVersion())
                || !credential.getPasswordVersion().equals(prepared.passwordVersion()) || credential.getPasswordHash()==null || !Objects.equals(credential.getPasswordHash(),prepared.passwordHash())
                || credential.getEmailVerifiedAt()==null || !prepared.expiresAt().isAfter(clock.now()))throw invalid();
        return sessions.verifiedSubject(prepared.authority(),subject.getId(),IdentitySessionStore.AuthenticationMethod.PASSWORD);
    }
    private Prepared limited(){return new Prepared(null,null,null,null,null,null,null,null,null,IdentityErrorCode.EMAIL_CHALLENGE_LIMIT);}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_PASSWORD_LOGIN);}
}
