package com.sma.admin.identity.internal.service;

import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IdentityPasswordReauthenticationStore {
    public record Attempt(AppUserPrincipal actor,IdentityReauthenticationStore.Action action,String target,String origin,String sourceAddress){
        @Override public String toString(){return "PasswordReauthenticationAttempt[REDACTED]";}
    }
    public record Prepared(AppUserPrincipal actor,IdentitySessionStore.ActorStamp stamp,IdentityReauthenticationStore.Action action,String target,
            Long credentialId,Long emailVersion,Long passwordVersion,String passwordHash,Instant expiresAt,IdentityErrorCode error){
        @Override public String toString(){return "PreparedPasswordReauthentication[REDACTED]";}
    }
    private final IdentityEmailBudget budgets;private final IdentitySessionStore sessions;private final IdentityAuthority authority;
    private final IdentityEmailCredentialMapper credentials;private final IdentityReauthenticationStore grants;
    private final IdentityPasswordWork.Settings settings;private final IdentityClock clock;private final JsonMapper json;
    public IdentityPasswordReauthenticationStore(IdentityEmailBudget budgets,IdentitySessionStore sessions,IdentityAuthority authority,IdentityEmailCredentialMapper credentials,
            IdentityReauthenticationStore grants,IdentityPasswordWork.Settings settings,IdentityClock clock,JsonMapper json){
        this.budgets=budgets;this.sessions=sessions;this.authority=authority;this.credentials=credentials;this.grants=grants;this.settings=settings;this.clock=clock;this.json=json;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Prepared prepare(Attempt input){
        IdentityAuthority.worker();if(input==null || input.actor()==null)throw invalid();ActorContext.requireTenant(input.actor().tenantId());IdentityEmailBudget.address(input.sourceAddress());
        var budget=budgets.lock();var stamp=sessions.currentActor(input.actor());var live=authority.match(stamp.authority());
        if(stamp.authority().platform().equals("WEB") && (input.origin()==null || !json.readValue(live.configuration().getOriginsJson(),new TypeReference<List<String>>(){}).contains(input.origin()))
                || !stamp.authority().platform().equals("WEB") && input.origin()!=null)throw invalid();
        if(!budgets.verification(budget,stamp.loginHandle(),input.sourceAddress()))return limited();
        String target=grants.target(stamp,input.actor(),input.action(),input.target());
        var credential=credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getSubjectId,input.actor().subjectId()).last("FOR SHARE"));
        String scope=credential==null?"subject:"+input.actor().subjectId():credential.getEmailIndex();
        if(!budgets.passwordTarget(input.actor().realmId(),scope,settings.perTargetPerMinute()))return limited();
        if(!live.realm().getPasswordEnabled() || credential==null || credential.getPasswordHash()==null || credential.getEmailVerifiedAt()==null || credential.getRealmId()!=input.actor().realmId())
            return new Prepared(input.actor(),stamp,input.action(),target,null,null,null,null,clock.now().plusSeconds(30),null);
        return new Prepared(input.actor(),stamp,input.action(),target,credential.getId(),credential.getEmailVersion(),credential.getPasswordVersion(),credential.getPasswordHash(),clock.now().plusSeconds(30),null);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public IdentityReauthenticationStore.Issued finish(Prepared prepared){
        IdentityAuthority.worker();if(prepared==null || prepared.error()!=null || prepared.credentialId()==null || prepared.passwordHash()==null || !prepared.expiresAt().isAfter(clock.now()))throw invalid();
        var current=sessions.currentActor(prepared.actor());if(!current.equals(prepared.stamp()))throw invalid();
        var credential=credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getId,prepared.credentialId()).last("FOR UPDATE"));
        if(credential==null || credential.getSubjectId()!=prepared.actor().subjectId() || credential.getRealmId()!=prepared.actor().realmId()
                || !credential.getEmailVersion().equals(prepared.emailVersion()) || !credential.getPasswordVersion().equals(prepared.passwordVersion())
                || !Objects.equals(credential.getPasswordHash(),prepared.passwordHash()) || credential.getEmailVerifiedAt()==null || !prepared.expiresAt().isAfter(clock.now()))throw invalid();
        return grants.issue(prepared.actor(),current,prepared.action(),prepared.target(),IdentityReauthenticationStore.Method.PASSWORD);
    }
    private Prepared limited(){return new Prepared(null,null,null,null,null,null,null,null,null,IdentityErrorCode.EMAIL_CHALLENGE_LIMIT);}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);}
}
