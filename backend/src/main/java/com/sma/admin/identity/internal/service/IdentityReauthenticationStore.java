package com.sma.admin.identity.internal.service;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.boot.context.properties.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** Grants are separate from sessions and are bound to one actor, action and target. Realm lock precedes grant locks. */
@Service @EnableConfigurationProperties(IdentityReauthenticationStore.Settings.class)
public class IdentityReauthenticationStore {
    public enum Action { EMAIL_BINDING, GOOGLE_BINDING, PASSWORD_REMOVAL, GOOGLE_REMOVAL }
    public enum Method { PASSWORD, GOOGLE }
    @ConfigurationProperties("sma.identity.reauthentication")
    public record Settings(Duration lifetime,Duration retention,Integer maximumPerRealm,Integer maximumPerSubject){
        public Settings{lifetime=lifetime==null?Duration.ofMinutes(2):lifetime;retention=retention==null?Duration.ofMinutes(10):retention;
            maximumPerRealm=maximumPerRealm==null?1000:maximumPerRealm;maximumPerSubject=maximumPerSubject==null?10:maximumPerSubject;
            if(lifetime.compareTo(Duration.ofSeconds(30))<0 || lifetime.compareTo(Duration.ofMinutes(5))>0 || retention.compareTo(lifetime)<0 || retention.compareTo(Duration.ofHours(1))>0
                    || maximumPerSubject<1 || maximumPerSubject>100 || maximumPerRealm<maximumPerSubject || maximumPerRealm>10000)throw new IllegalArgumentException("Invalid reauthentication budget");}
    }
    public record Issued(String grant,Instant expiresAt,Action action){@Override public String toString(){return "ReauthenticationGrant[REDACTED]";}}
    public record Use(String grant,Action action,String target){@Override public String toString(){return "ReauthenticationUse[REDACTED]";}}
    public record Verified(IdentitySessionStore.ActorStamp actor,Action action,String target,Method method){@Override public String toString(){return "VerifiedReauthentication[REDACTED]";}}
    private final IdentityReauthenticationMapper grants;private final IdentitySessionStore sessions;private final IdentityEmailIndex index;
    private final IdentityExternalMapper external;private final IdentityEmailBudget emailBudget;private final IdentityLoginBudget googleBudget;
    private final IdentityClock clock;private final Settings settings;private final JsonMapper json;private final SecureRandom random=new SecureRandom();
    public IdentityReauthenticationStore(IdentityReauthenticationMapper grants,IdentitySessionStore sessions,IdentityEmailIndex index,IdentityExternalMapper external,
            IdentityEmailBudget emailBudget,IdentityLoginBudget googleBudget,IdentityClock clock,Settings settings,JsonMapper json){
        this.grants=grants;this.sessions=sessions;this.index=index;this.external=external;this.emailBudget=emailBudget;this.googleBudget=googleBudget;this.clock=clock;this.settings=settings;this.json=json;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public String target(IdentitySessionStore.ActorStamp stamp,AppUserPrincipal actor,Action action,String raw){
        IdentityAuthority.worker();if(stamp==null || actor==null || action==null || raw==null)throw invalid();ActorContext.requireTenant(stamp.authority().tenantId());
        return switch(action){
            case EMAIL_BINDING->index.index(stamp.authority().realmId(),IdentityEmailAddress.normalize(raw));
            case GOOGLE_BINDING->{if(!raw.equals("google"))throw invalid();yield raw;}
            case PASSWORD_REMOVAL->{if(!raw.equals("password"))throw invalid();yield raw;}
            case GOOGLE_REMOVAL->{
                long id;try{if(!raw.matches("[1-9][0-9]{0,18}"))throw invalid();id=Long.parseLong(raw);}catch(NumberFormatException malformed){throw invalid();}
                var link=external.selectOne(new LambdaQueryWrapper<IdentityExternalEntity>().eq(IdentityExternalEntity::getId,id).last("FOR SHARE"));
                if(link==null || link.getSubjectId()!=actor.subjectId() || link.getRealmId()!=actor.realmId())throw invalid();yield Long.toString(id);
            }
        };
    }
    /** Only an identity proof verifier may issue. No generic application endpoint may accept a Verified object. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Issued issue(AppUserPrincipal actor,IdentitySessionStore.ActorStamp expected,Action action,String target,Method method){
        IdentityAuthority.worker();if(action==null || target==null || method==null)throw invalid();var current=sessions.currentActor(actor);if(!current.equals(expected))throw invalid();
        if(!canonical(action,target))throw invalid();cleanup(current.authority().realmId());
        var realm=grants.selectList(new LambdaQueryWrapper<IdentityReauthenticationEntity>().select(IdentityReauthenticationEntity::getId,IdentityReauthenticationEntity::getSubjectId)
                .eq(IdentityReauthenticationEntity::getRealmId,actor.realmId()).last("LIMIT 10001 FOR UPDATE"));
        if(realm.size()>=settings.maximumPerRealm() || realm.stream().filter(row->row.getSubjectId()==actor.subjectId()).count()>=settings.maximumPerSubject())throw new BizException(IdentityErrorCode.REAUTHENTICATION_LIMIT);
        String token=token();var row=new IdentityReauthenticationEntity();row.setRealmId(actor.realmId());row.setSubjectId(actor.subjectId());row.setSessionId(actor.sessionId());
        row.setStampJson(json.writeValueAsString(current));row.setAction(action.name());row.setTargetRef(target);row.setMethod(method.name());row.setGrantHash(IdentitySessionStore.hashToken(token));row.setStatus("ACTIVE");
        row.setExpiresAt(clock.now().plus(settings.lifetime()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));row.setRetainUntil(clock.now().plus(settings.retention()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        grants.insert(row);return new Issued(token,row.getExpiresAt(),action);
    }
    /** Owning operation must call this before acquiring identity locks; callbacks may perform only same-resource mutations. */
    @Transactional(propagation=Propagation.MANDATORY)
    public <T>T consume(AppUserPrincipal actor,Use input,Function<Verified,T> mutation){
        IdentityAuthority.worker();if(input==null || input.action()==null || input.grant()==null || !input.grant().matches("[A-Za-z0-9_-]{43}") || mutation==null)throw invalid();
        // These budgets must precede realm/actor/grant locks because callbacks create their native transactions.
        if(input.action()==Action.EMAIL_BINDING)emailBudget.lock();else if(input.action()==Action.GOOGLE_BINDING)googleBudget.lock();
        var current=sessions.currentActor(actor);String target=target(current,actor,input.action(),input.target());
        var row=grants.selectOne(new LambdaQueryWrapper<IdentityReauthenticationEntity>().eq(IdentityReauthenticationEntity::getGrantHash,IdentitySessionStore.hashToken(input.grant())).last("FOR UPDATE"));
        if(row==null || !row.getStatus().equals("ACTIVE") || !row.getExpiresAt().isAfter(clock.now()) || row.getSubjectId()!=actor.subjectId() || row.getSessionId()!=actor.sessionId()
                || row.getRealmId()!=actor.realmId() || !row.getAction().equals(input.action().name()) || !row.getTargetRef().equals(target)
                || !json.readValue(row.getStampJson(),IdentitySessionStore.ActorStamp.class).equals(current))throw invalid();
        T result=mutation.apply(new Verified(current,input.action(),target,Method.valueOf(row.getMethod())));
        row.setStatus("CONSUMED");row.setGrantHash(null);row.setConsumedAt(clock.now());grants.updateById(row);return result;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public int maintain(){IdentityAuthority.worker();return cleanup(null);}
    private int cleanup(Long realm){
        var expiredQuery=new LambdaQueryWrapper<IdentityReauthenticationEntity>().eq(IdentityReauthenticationEntity::getStatus,"ACTIVE").le(IdentityReauthenticationEntity::getExpiresAt,clock.now());
        if(realm!=null)expiredQuery.eq(IdentityReauthenticationEntity::getRealmId,realm);
        var expired=grants.selectList(expiredQuery.orderByAsc(IdentityReauthenticationEntity::getId).last("LIMIT 100 FOR UPDATE"));
        for(var row:expired){row.setStatus("EXPIRED");row.setGrantHash(null);grants.updateById(row);}
        var retained=new LambdaQueryWrapper<IdentityReauthenticationEntity>().ne(IdentityReauthenticationEntity::getStatus,"ACTIVE").le(IdentityReauthenticationEntity::getRetainUntil,clock.now());
        if(realm!=null)retained.eq(IdentityReauthenticationEntity::getRealmId,realm);
        var old=grants.selectList(retained.orderByAsc(IdentityReauthenticationEntity::getId).last("LIMIT 100 FOR UPDATE"));old.forEach(row->grants.purge(row.getId()));return expired.size()+old.size();
    }
    private boolean canonical(Action action,String target){return switch(action){case EMAIL_BINDING->target.matches("[0-9a-f]{64}");case GOOGLE_BINDING->target.equals("google");case PASSWORD_REMOVAL->target.equals("password");case GOOGLE_REMOVAL->target.matches("[1-9][0-9]{0,18}");};}
    private String token(){byte[] value=new byte[32];random.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);}
}
