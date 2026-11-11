package com.sma.admin.identity.internal.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.AppUserPrincipal;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.boot.context.properties.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @EnableConfigurationProperties(IdentityExternalLinks.Settings.class)
public class IdentityExternalLinks {
    @ConfigurationProperties("sma.identity.bindings")
    public record Settings(Integer maximumGoogleLinks,Integer maximumRememberedGoogleLinks){
        public Settings{maximumGoogleLinks=maximumGoogleLinks==null?5:maximumGoogleLinks;maximumRememberedGoogleLinks=maximumRememberedGoogleLinks==null?100:maximumRememberedGoogleLinks;
            if(maximumGoogleLinks<1 || maximumGoogleLinks>100 || maximumRememberedGoogleLinks<maximumGoogleLinks || maximumRememberedGoogleLinks>1000)throw new IllegalArgumentException("Invalid external identity limits");}
    }
    public record Linked(Long linkId,boolean changed,boolean signInRequired){}
    private final IdentityExternalMapper external;private final IdentitySessionStore sessions;private final IdentityClock clock;private final Settings settings;
    public IdentityExternalLinks(IdentityExternalMapper external,IdentitySessionStore sessions,IdentityClock clock,Settings settings){this.external=external;this.sessions=sessions;this.clock=clock;this.settings=settings;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireOwned(AppUserPrincipal actor,GoogleIdTokenVerifier.Verified identity){
        IdentityAuthority.worker();sessions.currentActor(actor);
        if(identity==null || !"https://accounts.google.com".equals(identity.issuer()))throw new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);
        var row=external.history(actor.realmId(),identity.issuer(),identity.subject());
        if(row==null || row.getDeleted() || row.getSubjectId()!=actor.subjectId())throw new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);
    }
    /** Only the completed, purpose-bound Google operation may call this method. Never expose Verified as a request DTO. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Linked add(AppUserPrincipal actor,IdentitySessionStore.ActorStamp expected,GoogleIdTokenVerifier.Verified identity){
        IdentityAuthority.worker();if(identity==null || !"https://accounts.google.com".equals(identity.issuer()) || identity.subject()==null || !identity.subject().matches("[!-~]{1,255}"))throw conflict();
        if(!sessions.currentActor(actor).equals(expected))throw new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);
        var row=external.history(actor.realmId(),identity.issuer(),identity.subject());
        if(row!=null && row.getSubjectId()!=actor.subjectId())throw conflict();
        if(row!=null && !row.getDeleted())return new Linked(row.getId(),false,false);
        long active=external.selectList(new LambdaQueryWrapper<IdentityExternalEntity>().select(IdentityExternalEntity::getId).eq(IdentityExternalEntity::getSubjectId,actor.subjectId()).last("LIMIT 101 FOR UPDATE")).size();
        if(active>=settings.maximumGoogleLinks() || row==null && external.remembered(actor.subjectId()).size()>=settings.maximumRememberedGoogleLinks())throw new BizException(IdentityErrorCode.IDENTITY_LINK_LIMIT);
        if(row==null){
            row=new IdentityExternalEntity();row.setRealmId(actor.realmId());row.setSubjectId(actor.subjectId());row.setIssuer(identity.issuer());row.setExternalSubject(identity.subject());
            try{external.insert(row);}catch(DuplicateKeyException race){throw conflict();}
        }else if(external.restore(row.getId(),actor.subjectId(),clock.now())!=1)throw conflict();
        sessions.revokeSubject(actor.subjectId());return new Linked(row.getId(),true,true);
    }
    private BizException conflict(){return new BizException(IdentityErrorCode.IDENTITY_LINK_CONFLICT);}
}
