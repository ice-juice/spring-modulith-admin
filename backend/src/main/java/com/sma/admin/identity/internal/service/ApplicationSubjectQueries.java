package com.sma.admin.identity.internal.service;

import java.util.*;
import java.util.function.Function;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.*;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative reads; no query result, contact or authorization decision is cached. Policies run before identity row locks. */
@Service @Transactional
public class ApplicationSubjectQueries implements SubjectQuery,SubjectContactQuery {
    private final SubjectAccessPolicies policies;private final IdentitySessionStore sessions;private final TenantEntryQuery tenants;
    private final IdentityRealmMapper realms;private final IdentityClientMapper clients;private final IdentitySubjectMapper subjects;
    private final IdentityEmailCredentialMapper credentials;private final TenantSecretCodec secrets;
    public ApplicationSubjectQueries(SubjectAccessPolicies policies,IdentitySessionStore sessions,TenantEntryQuery tenants,IdentityRealmMapper realms,
            IdentityClientMapper clients,IdentitySubjectMapper subjects,IdentityEmailCredentialMapper credentials,TenantSecretCodec secrets){
        this.policies=policies;this.sessions=sessions;this.tenants=tenants;this.realms=realms;this.clients=clients;this.subjects=subjects;this.credentials=credentials;this.secrets=secrets;
    }
    @Override public Subject current(String application){
        var actor=ActorContext.requireApplicationUser(application);
        var request=new SubjectReadRequest(application,actor.realmId(),actor.subjectId(),"self");
        return read(request,SubjectAccessPolicy.Content.BASIC,true,this::view).orElseThrow(()->new BizException(IdentityErrorCode.INVALID_SESSION));
    }
    @Override public Optional<Subject> find(SubjectReadRequest request){return read(request,SubjectAccessPolicy.Content.BASIC,false,this::view);}
    @Override public Optional<VerifiedEmail> verifiedEmail(SubjectReadRequest request){
        return read(request,SubjectAccessPolicy.Content.VERIFIED_EMAIL,false,loaded->{
            if(!loaded.subject().getEnabled())return null;
            var row=credentials.selectOne(new LambdaQueryWrapper<IdentityEmailCredentialEntity>().eq(IdentityEmailCredentialEntity::getSubjectId,loaded.subject().getId())
                    .eq(IdentityEmailCredentialEntity::getRealmId,loaded.request().realmId()).last("FOR SHARE"));
            if(row==null || row.getEmailVerifiedAt()==null)return null;
            try(var value=secrets.decrypt(IdentityEmailAccountService.reference(row),"verified-email-address",row.getEmailEnvelope())){
                char[] chars=value.copy();try{return new VerifiedEmail(view(loaded),new String(chars),row.getEmailVerifiedAt());}finally{Arrays.fill(chars,'\0');}
            }
        });
    }
    private record Loaded(SubjectReadRequest request,IdentitySubjectEntity subject){}
    private Subject view(Loaded loaded){var row=loaded.subject();return new Subject(row.getTenantId(),row.getRealmId(),row.getId(),loaded.request().application(),row.getEnabled());}
    private <T>Optional<T> read(SubjectReadRequest request,SubjectAccessPolicy.Content content,boolean self,Function<Loaded,T> projection){
        if(request==null)throw new IllegalArgumentException("Subject read request required");var actor=ActorContext.require();AppUserPrincipal principal=null;
        if(actor.kind()==ActorRef.Kind.APP_USER){principal=ActorContext.requireApplicationUser(request.application());if(principal.realmId()!=request.realmId())throw new AccessDeniedException("Subject realm denied");}
        if(!self)policies.require(actor,request,content);
        final AppUserPrincipal app=principal;
        return ActorContext.withinJob(actor.tenantId(),"identity-subject-query",()->{
            if(!tenants.securityState(actor.tenantId()).map(TenantEntryQuery.SecurityState::acceptsBusiness).orElse(false))throw new AccessDeniedException("Subject tenant unavailable");
            if(app!=null)sessions.currentActor(app);
            var realm=realms.selectOne(new LambdaQueryWrapper<IdentityRealmEntity>().eq(IdentityRealmEntity::getId,request.realmId()).last("FOR SHARE"));
            if(realm==null || !realm.getEnabled())return Optional.empty();
            var linked=clients.selectList(new LambdaQueryWrapper<IdentityClientEntity>().select(IdentityClientEntity::getId).eq(IdentityClientEntity::getRealmId,request.realmId())
                    .eq(IdentityClientEntity::getApplication,request.application()).eq(IdentityClientEntity::getEnabled,true).last("LIMIT 1 FOR SHARE"));
            if(linked.isEmpty())return Optional.empty();
            var subject=subjects.selectOne(new LambdaQueryWrapper<IdentitySubjectEntity>().eq(IdentitySubjectEntity::getId,request.subjectId())
                    .eq(IdentitySubjectEntity::getRealmId,request.realmId()).last("FOR SHARE"));
            return subject==null?Optional.empty():Optional.ofNullable(projection.apply(new Loaded(request,subject)));
        });
    }
}
