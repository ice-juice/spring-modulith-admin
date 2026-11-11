package com.sma.admin.identity.internal.service;

import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Short transactions only. One tenant budget lock precedes transaction/authority/subject/session locks. */
@Service @EnableConfigurationProperties(IdentityLoginProperties.class)
@Transactional(propagation=Propagation.REQUIRES_NEW)
public class IdentityLoginStore {
    public enum Flow { CODE, ID_TOKEN }
    public enum OperationKind { REAUTH, LINK_GOOGLE }
    public record Operation(OperationKind kind,long subjectId,IdentitySessionStore.ActorStamp actor,IdentityReauthenticationStore.Action action,String target){
        @Override public String toString(){return "GoogleOperation[REDACTED]";}
    }
    public record Start(String loginHandle,String callbackUri,String origin,Flow flow,String pkceChallenge,String sourceAddress){
        @Override public String toString(){return "LoginStart[REDACTED]";}
    }
    public record Started(String state,String proof,String nonce,URI authorizationUri,String googleClientId,String platform,Instant expiresAt){
        @Override public String toString(){return "LoginStarted[REDACTED]";}
    }
    public record Completion(String state,String proof,String verifier,String loginHandle,String callbackUri,String origin,Flow flow,String credential){
        @Override public String toString(){return "LoginCompletion[REDACTED]";}
    }
    public record Prepared(long transactionId,String claimId,IdentityAuthority.Snapshot snapshot,IdentityClientRevisionEntity configuration,String nonce,String callbackUri,Flow flow,Operation operation){
        public Prepared(long transactionId,String claimId,IdentityAuthority.Snapshot snapshot,IdentityClientRevisionEntity configuration,String nonce,String callbackUri,Flow flow){
            this(transactionId,claimId,snapshot,configuration,nonce,callbackUri,flow,null);
        }
        @Override public String toString(){return "PreparedLogin[REDACTED]";}
    }
    public record Claim(Prepared prepared,IdentityErrorCode error){}
    private final IdentityLoginTransactionMapper transactions;private final IdentityLoginRateMapper rates;private final IdentityLoginBudget budgets;
    private final IdentityAuthority authority;private final IdentitySessionStore sessions;private final GoogleProtocol google;
    private final TenantSecretCodec secrets;private final JsonMapper json;private final IdentityClock clock;private final IdentityLoginProperties settings;
    private final IdentityReauthenticationStore reauthentication;private final IdentityExternalMapper external;
    private final SecureRandom random=new SecureRandom();
    public IdentityLoginStore(IdentityLoginTransactionMapper transactions,IdentityLoginRateMapper rates,IdentityLoginBudget budgets,IdentityAuthority authority,
            IdentitySessionStore sessions,GoogleProtocol google,TenantSecretCodec secrets,JsonMapper json,IdentityClock clock,IdentityLoginProperties settings,
            IdentityReauthenticationStore reauthentication,IdentityExternalMapper external){
        this.transactions=transactions;this.rates=rates;this.budgets=budgets;this.authority=authority;this.sessions=sessions;this.google=google;
        this.secrets=secrets;this.json=json;this.clock=clock;this.settings=settings;
        this.reauthentication=reauthentication;this.external=external;
    }
    public Started begin(Start input){
        return create(input,null,null,null,null,null);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Started beginReauthentication(Start input,AppUserPrincipal actor,IdentityReauthenticationStore.Action action,String target){
        return create(input,OperationKind.REAUTH,actor,action,target,null);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Started beginLink(Start input,AppUserPrincipal actor,IdentityReauthenticationStore.Verified approval){
        return create(input,OperationKind.LINK_GOOGLE,actor,IdentityReauthenticationStore.Action.GOOGLE_BINDING,"google",approval);
    }
    private Started create(Start input,OperationKind kind,AppUserPrincipal actor,IdentityReauthenticationStore.Action action,String target,IdentityReauthenticationStore.Verified approval){
        IdentityAuthority.worker();if(input==null || input.flow()==null || input.pkceChallenge()==null || !input.pkceChallenge().matches("[A-Za-z0-9_-]{43}"))throw invalid();
        var budget=budgets.lock();cleanup(budget);
        var snapshot=authority.capture(input.loginHandle());var live=authority.match(snapshot);var configuration=live.configuration();
        validateContext(snapshot,configuration,input.callbackUri(),input.origin());
        Operation operation=null;
        if(kind!=null){
            var stamp=sessions.currentActor(actor);if(!stamp.authority().equals(snapshot))throw new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);
            if(kind==OperationKind.REAUTH){
                if(external.selectCount(new LambdaQueryWrapper<IdentityExternalEntity>().eq(IdentityExternalEntity::getSubjectId,actor.subjectId()).eq(IdentityExternalEntity::getIssuer,"https://accounts.google.com"))==0)
                    throw new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);
            }else if(approval==null || approval.action()!=IdentityReauthenticationStore.Action.GOOGLE_BINDING || !approval.target().equals("google") || !approval.actor().equals(stamp))throw new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);
            operation=new Operation(kind,actor.subjectId(),stamp,action,reauthentication.target(stamp,actor,action,target));
        }
        budgets.reserve(budget,snapshot.clientId(),input.sourceAddress());
        String state=random(),proof=random(),nonce=random();var row=new IdentityLoginTransactionEntity();row.setId(IdWorker.getId());row.setTenantId(snapshot.tenantId());
        row.setStateHash(hash(state));row.setProofHash(hash(proof));row.setSnapshotJson(json.writeValueAsString(snapshot));row.setCallbackUri(input.callbackUri());
        row.setOrigin(input.origin());row.setFlow(input.flow().name());row.setPkceChallenge(input.pkceChallenge());row.setStatus("PENDING");
        row.setIntent(kind==null?"LOGIN":kind.name());row.setOperationJson(operation==null?null:json.writeValueAsString(operation));
        row.setExpiresAt(clock.now().plus(settings.lifetime()));row.setRetainUntil(clock.now().plus(settings.retention()));
        char[] chars=nonce.toCharArray();try{row.setNonceEnvelope(secrets.encrypt(reference(row),"google-login-nonce",chars));}finally{Arrays.fill(chars,'\0');}
        transactions.insert(row);
        URI authorization=input.flow()==Flow.CODE?google.authorization(configuration,input.callbackUri(),state,nonce,input.pkceChallenge(),operation!=null):null;
        return new Started(state,proof,nonce,authorization,configuration.getGoogleClientId(),snapshot.platform(),row.getExpiresAt());
    }
    public Claim claim(Completion input){
        return claimRecord(input,null,null);
    }
    public Claim claimOperation(Completion input,AppUserPrincipal actor,OperationKind expected){
        if(expected==null)throw invalid();return claimRecord(input,actor,expected);
    }
    private Claim claimRecord(Completion input,AppUserPrincipal actor,OperationKind expected){
        IdentityAuthority.worker();validate(input);String state=hash(input.state());String proof=hash(input.proof());String challenge=GoogleProtocol.challenge(input.verifier());
        var budget=budgets.lock();var row=transactions.selectOne(new LambdaQueryWrapper<IdentityLoginTransactionEntity>().eq(IdentityLoginTransactionEntity::getStateHash,state).last("FOR UPDATE"));
        if(row==null || !equal(proof,row.getProofHash()) || !equal(challenge,row.getPkceChallenge())
                || !Objects.equals(row.getCallbackUri(),input.callbackUri()) || !Objects.equals(row.getOrigin(),input.origin()) || !row.getFlow().equals(input.flow().name()))throw invalid();
        if(!row.getIntent().equals(expected==null?"LOGIN":expected.name()))throw invalid();
        var snapshot=json.readValue(row.getSnapshotJson(),IdentityAuthority.Snapshot.class);ActorContext.requireTenant(snapshot.tenantId());
        if(!row.getStatus().equals("PENDING"))return new Claim(null,IdentityErrorCode.LOGIN_USED);
        if(!row.getExpiresAt().isAfter(clock.now())){terminal(budget,row,"FAILED","EXPIRED");return new Claim(null,IdentityErrorCode.LOGIN_EXPIRED);}
        IdentityAuthority.Loaded live;
        try{live=authority.match(snapshot);}catch(BizException changed){terminal(budget,row,"FAILED","CONFIGURATION_CHANGED");return new Claim(null,IdentityErrorCode.INVALID_SESSION);}
        if(!live.client().getLoginHandle().equals(input.loginHandle()))throw invalid();
        validateContext(snapshot,live.configuration(),row.getCallbackUri(),row.getOrigin());
        var operation=operation(row);
        if(operation!=null){
            try{validateActor(operation,actor,snapshot);}
            catch(BizException changed){terminal(budget,row,"FAILED","ACTOR_CHANGED");return new Claim(null,IdentityErrorCode.INVALID_REAUTHENTICATION);}
        }
        String nonce;
        try(var value=secrets.decrypt(reference(row),"google-login-nonce",row.getNonceEnvelope())){
            char[] chars=value.copy();try{nonce=new String(chars);}finally{Arrays.fill(chars,'\0');}
        }
        row.setStatus("VERIFYING");row.setClaimId(UUID.randomUUID().toString());transactions.updateById(row);
        return new Claim(new Prepared(row.getId(),row.getClaimId(),snapshot,live.configuration(),nonce,row.getCallbackUri(),input.flow(),operation),null);
    }
    /** Session issuance and transaction completion share one commit; a failed commit yields no successful response. */
    public IdentitySessionStore.Issued finish(Prepared prepared,GoogleIdTokenVerifier.Verified verified){
        IdentityAuthority.worker();ActorContext.requireTenant(prepared.snapshot().tenantId());var budget=budgets.lock();var row=claimed(prepared);
        if(!row.getIntent().equals("LOGIN") || prepared.operation()!=null)throw invalid();
        if(!row.getExpiresAt().isAfter(clock.now()))throw new BizException(IdentityErrorCode.LOGIN_EXPIRED);
        var snapshot=json.readValue(row.getSnapshotJson(),IdentityAuthority.Snapshot.class);
        if(!snapshot.equals(prepared.snapshot()))throw invalid();
        var tokens=sessions.google(snapshot,verified);row.setSessionId(tokens.sessionId());terminal(budget,row,"COMPLETED","VERIFIED");return tokens;
    }
    public <T>T finishOperation(Prepared prepared,AppUserPrincipal actor,Function<Operation,T> work){
        IdentityAuthority.worker();ActorContext.requireTenant(prepared.snapshot().tenantId());var budget=budgets.lock();var row=claimed(prepared);
        if(!row.getExpiresAt().isAfter(clock.now()) || prepared.operation()==null || work==null)throw new BizException(IdentityErrorCode.LOGIN_EXPIRED);
        var saved=operation(row);var snapshot=json.readValue(row.getSnapshotJson(),IdentityAuthority.Snapshot.class);
        if(saved==null || !saved.equals(prepared.operation()) || !snapshot.equals(prepared.snapshot()))throw invalid();validateActor(saved,actor,snapshot);
        T result=work.apply(saved);terminal(budget,row,"COMPLETED",saved.kind()==OperationKind.REAUTH?"REAUTHENTICATED":"GOOGLE_LINKED");return result;
    }
    private Operation operation(IdentityLoginTransactionEntity row){
        if(row.getIntent().equals("LOGIN")){if(row.getOperationJson()!=null)throw invalid();return null;}
        if(row.getOperationJson()==null)throw invalid();var operation=json.readValue(row.getOperationJson(),Operation.class);
        if(operation==null || !operation.kind().name().equals(row.getIntent()))throw invalid();return operation;
    }
    private void validateActor(Operation operation,AppUserPrincipal actor,IdentityAuthority.Snapshot snapshot){
        if(actor==null || actor.subjectId()!=operation.subjectId() || !operation.actor().authority().equals(snapshot) || !sessions.currentActor(actor).equals(operation.actor()))
            throw new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);
    }
    /** Separate failure transaction, including after finish rolled back. Never persist raw provider messages. */
    public void fail(Prepared prepared){
        IdentityAuthority.worker();ActorContext.requireTenant(prepared.snapshot().tenantId());var budget=budgets.lock();
        var row=transactions.selectOne(new LambdaQueryWrapper<IdentityLoginTransactionEntity>().eq(IdentityLoginTransactionEntity::getId,prepared.transactionId()).last("FOR UPDATE"));
        if(row!=null && row.getStatus().equals("VERIFYING") && Objects.equals(row.getClaimId(),prepared.claimId()))terminal(budget,row,"FAILED","VERIFICATION_FAILED");
    }
    public int maintain(){IdentityAuthority.worker();var budget=budgets.existing();return budget==null?0:cleanup(budget);}
    private int cleanup(IdentityLoginBudgetEntity budget){
        int changed=0;
        var expired=transactions.selectList(new LambdaQueryWrapper<IdentityLoginTransactionEntity>().in(IdentityLoginTransactionEntity::getStatus,List.of("PENDING","VERIFYING"))
                .le(IdentityLoginTransactionEntity::getExpiresAt,clock.now()).orderByAsc(IdentityLoginTransactionEntity::getId).last("LIMIT 100 FOR UPDATE"));
        for(var row:expired){terminal(budget,row,"FAILED",row.getStatus().equals("VERIFYING")?"VERIFICATION_EXPIRED":"EXPIRED");changed++;}
        var purge=transactions.selectList(new LambdaQueryWrapper<IdentityLoginTransactionEntity>().in(IdentityLoginTransactionEntity::getStatus,List.of("COMPLETED","FAILED"))
                .le(IdentityLoginTransactionEntity::getRetainUntil,clock.now()).orderByAsc(IdentityLoginTransactionEntity::getId).last("LIMIT 100 FOR UPDATE"));
        for(var row:purge){transactions.purge(row.getId());budgets.purged(budget);changed++;}
        var expiredRates=rates.selectList(new LambdaQueryWrapper<IdentityLoginRateEntity>().le(IdentityLoginRateEntity::getExpiresAt,clock.now()).last("LIMIT 100 FOR UPDATE"));
        for(var row:expiredRates){rates.purge(row.getId());changed++;}
        return changed;
    }
    private IdentityLoginTransactionEntity claimed(Prepared prepared){
        var row=transactions.selectOne(new LambdaQueryWrapper<IdentityLoginTransactionEntity>().eq(IdentityLoginTransactionEntity::getId,prepared.transactionId()).last("FOR UPDATE"));
        if(row==null || !row.getStatus().equals("VERIFYING") || !Objects.equals(row.getClaimId(),prepared.claimId()))throw new BizException(IdentityErrorCode.LOGIN_USED);return row;
    }
    private void terminal(IdentityLoginBudgetEntity budget,IdentityLoginTransactionEntity row,String state,String result){
        if(!Set.of("PENDING","VERIFYING").contains(row.getStatus()))throw new IllegalStateException("Login already terminal");
        row.setStatus(state);row.setResultCode(result);row.setNonceEnvelope(null);budgets.terminal(budget);transactions.updateById(row);
    }
    private void validateContext(IdentityAuthority.Snapshot snapshot,IdentityClientRevisionEntity configuration,String callback,String origin){
        if(!configuration.getGoogleEnabled() || callback==null || !json.readValue(configuration.getCallbacksJson(),new TypeReference<List<String>>(){}).contains(callback))throw invalid();
        if(snapshot.platform().equals("WEB")){
            if(origin==null || !json.readValue(configuration.getOriginsJson(),new TypeReference<List<String>>(){}).contains(origin))throw invalid();
        }else if(origin!=null)throw invalid();
    }
    private void validate(Completion input){
        if(input==null || input.flow()==null || input.credential()==null || input.credential().isBlank() || input.credential().length()>16384
                || input.credential().chars().anyMatch(Character::isISOControl))throw invalid();
    }
    private TenantConfigurationRef reference(IdentityLoginTransactionEntity row){return new TenantConfigurationRef(row.getTenantId(),"identity",row.getId(),1,1);}
    private String random(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private String hash(String value){return IdentitySessionStore.hashToken(value);}
    private boolean equal(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII));}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_GOOGLE_PROOF);}
}
