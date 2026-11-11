package com.sma.admin.identity.internal.service;

import java.security.*;
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

/** Internal state machine. Creation/delivery and grant/domain mutation share the owner's transaction; guesses never do. */
@Service @EnableConfigurationProperties(IdentityEmailProperties.class)
public class IdentityEmailChallengeStore {
    private static final class WrongClient extends BizException {WrongClient(){super(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);}}
    public enum Purpose { REGISTRATION, PASSWORD_RESET, EMAIL_BINDING }
    public record Start(String loginHandle,String language,Purpose purpose,String address,Long subjectId,Long subjectGeneration,String origin,String sourceAddress){
        @Override public String toString(){return "EmailChallengeStart[REDACTED]";}
    }
    public record Prepared(long challengeId,String handle,String proof,SecretValue code,String address,String templateCode,String scene,String language,Instant expiresAt) implements AutoCloseable {
        @Override public String toString(){return "PreparedEmailChallenge[REDACTED]";}
        @Override public void close(){code.close();}
    }
    public record Verification(String handle,String proof,String code,String loginHandle,Purpose purpose,String origin,String sourceAddress){
        @Override public String toString(){return "EmailVerification[REDACTED]";}
    }
    public record Result(String grant,Instant expiresAt,IdentityErrorCode error){
        @Override public String toString(){return "EmailVerificationResult[REDACTED]";}
    }
    public record Use(String grant,String proof,String loginHandle,Purpose purpose,String origin){@Override public String toString(){return "EmailGrantUse[REDACTED]";}}
    public record Granted(long challengeId,IdentityAuthority.Snapshot authority,String address,String targetIndex,Long subjectId,Long subjectGeneration){
        @Override public String toString(){return "GrantedEmailIdentity[REDACTED]";}
    }
    private final IdentityEmailChallengeMapper challenges;private final IdentityEmailConfigurationMapper configurations;private final IdentitySubjectMapper subjects;
    private final IdentityEmailBudget budgets;private final IdentityAuthority authority;private final IdentityEmailIndex index;private final TenantSecretCodec secrets;
    private final IdentityEmailProperties settings;private final IdentityClock clock;private final JsonMapper json;private final SecureRandom random=new SecureRandom();
    private final IdentitySessionStore sessions;
    public IdentityEmailChallengeStore(IdentityEmailChallengeMapper challenges,IdentityEmailConfigurationMapper configurations,IdentitySubjectMapper subjects,
            IdentityEmailBudget budgets,IdentityAuthority authority,IdentityEmailIndex index,TenantSecretCodec secrets,IdentityEmailProperties settings,IdentityClock clock,JsonMapper json,IdentitySessionStore sessions){
        this.challenges=challenges;this.configurations=configurations;this.subjects=subjects;this.budgets=budgets;this.authority=authority;this.index=index;
        this.secrets=secrets;this.settings=settings;this.clock=clock;this.json=json;this.sessions=sessions;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Prepared begin(Start input){
        IdentityAuthority.worker();if(input==null || input.purpose()==null || !Set.of("zh-CN","en-US").contains(input.language()==null?"":input.language()))throw invalid();
        IdentityEmailBudget.address(input.sourceAddress());String address=IdentityEmailAddress.normalize(input.address());var budget=budgets.lock();cleanup(budget);
        var snapshot=authority.capture(input.loginHandle());var live=authority.match(snapshot);
        if(!live.realm().getPasswordEnabled() || input.purpose()==Purpose.REGISTRATION && !live.realm().getRegistrationEnabled())throw invalid();
        origin(snapshot,live.configuration(),input.origin());
        if(input.purpose()==Purpose.REGISTRATION && (input.subjectId()!=null || input.subjectGeneration()!=null)
                || input.purpose()==Purpose.EMAIL_BINDING && input.subjectId()==null || (input.subjectId()==null)!=(input.subjectGeneration()==null))throw invalid();
        if(input.subjectId()!=null)subject(input.subjectId(),input.subjectGeneration(),snapshot.realmId());
        var configuration=configurations.selectOne(new LambdaQueryWrapper<IdentityEmailConfigurationEntity>().eq(IdentityEmailConfigurationEntity::getClientId,snapshot.clientId())
                .eq(IdentityEmailConfigurationEntity::getLanguage,input.language()).last("FOR SHARE"));
        if(configuration==null || !configuration.getEnabled())throw new BizException(IdentityErrorCode.EMAIL_NOT_CONFIGURED);
        String target=index.index(snapshot.realmId(),address);budgets.reserve(budget,snapshot.clientId(),snapshot.realmId(),target,input.sourceAddress());
        String handle=token(),proof=token();char[] code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000)).toCharArray();
        var row=new IdentityEmailChallengeEntity();row.setId(IdWorker.getId());row.setTenantId(snapshot.tenantId());row.setHandleHash(hash(handle));row.setProofHash(hash(proof));
        row.setSnapshotJson(json.writeValueAsString(snapshot));row.setEmailConfigurationId(configuration.getId());row.setEmailConfigurationVersion(configuration.getVersion());
        row.setLanguage(input.language());row.setPurpose(input.purpose().name());row.setOrigin(input.origin());row.setTargetIndex(target);row.setSubjectId(input.subjectId());row.setSubjectGeneration(input.subjectGeneration());
        row.setStatus("PREPARED");row.setFailures(0);row.setExpiresAt(clock.now().plus(settings.lifetime()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        row.setRetainUntil(clock.now().plus(settings.retention()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        char[] targetValue=address.toCharArray();
        try{
            row.setCodeFingerprint(secrets.fingerprint(reference(row),"email-challenge-code",code));
            row.setTargetEnvelope(secrets.encrypt(reference(row),"email-challenge-target",targetValue));challenges.insert(row);
            String template=switch(input.purpose()){case REGISTRATION->configuration.getRegistrationTemplate();case PASSWORD_RESET->configuration.getResetTemplate();case EMAIL_BINDING->configuration.getBindingTemplate();};
            return new Prepared(row.getId(),handle,proof,new SecretValue(code),address,template,scene(input.purpose()),input.language(),row.getExpiresAt());
        }finally{Arrays.fill(code,'\0');Arrays.fill(targetValue,'\0');}
    }
    /** The owner must enqueue the real delivery and attach its returned ID in the same transaction as begin. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void attachDelivery(long challengeId,long deliveryId){
        IdentityAuthority.worker();budgets.lock();var row=challenges.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().eq(IdentityEmailChallengeEntity::getId,challengeId).last("FOR UPDATE"));
        if(deliveryId<=0 || row==null || !row.getStatus().equals("PREPARED") || !row.getExpiresAt().isAfter(clock.now()))throw invalid();
        row.setDeliveryId(deliveryId);row.setStatus("PENDING");challenges.updateById(row);
    }
    /** Return a denial instead of throwing it inside this transaction. Outer failure cannot refund guesses or a successful grant. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Result verify(Verification input){
        return verifyRecord(input,null);
    }
    /** The protected HTTP binding flow also binds verification to the original actor, not just final consumption. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Result verifyBinding(Verification input,AppUserPrincipal actor){
        IdentityAuthority.worker();
        if(input==null || input.purpose()!=Purpose.EMAIL_BINDING || actor==null)return denied(IdentityErrorCode.INVALID_REAUTHENTICATION);
        return verifyRecord(input,actor);
    }
    private Result verifyRecord(Verification input,AppUserPrincipal actor){
        IdentityAuthority.worker();
        if(input==null || input.purpose()==null || !token(input.handle()) || !token(input.proof()) || !token(input.loginHandle()) || input.code()==null || !input.code().matches("[0-9]{6}"))return denied(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);
        IdentityEmailBudget.address(input.sourceAddress());var budget=budgets.lock();
        if(!budgets.verification(budget,input.loginHandle(),input.sourceAddress()))return denied(IdentityErrorCode.EMAIL_CHALLENGE_LIMIT);
        var row=challenges.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().eq(IdentityEmailChallengeEntity::getHandleHash,hash(input.handle())).last("FOR UPDATE"));
        if(row==null || !proof(row,input.proof(),input.purpose(),input.origin()))return denied(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);
        if(!row.getStatus().equals("PENDING"))return denied(IdentityErrorCode.EMAIL_CHALLENGE_USED);
        if(!row.getExpiresAt().isAfter(clock.now())){terminal(budget,row,"EXPIRED");return denied(IdentityErrorCode.EMAIL_CHALLENGE_EXPIRED);}
        try{current(row,input.loginHandle());}
        catch(WrongClient mismatch){return denied(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);}
        catch(BizException changed){terminal(budget,row,"FAILED");return denied(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);}
        if(actor!=null){
            try{
                if(row.getSubjectId()==null || row.getSubjectId()!=actor.subjectId() || row.getBindingActorJson()==null
                        || !sessions.currentActor(actor).equals(json.readValue(row.getBindingActorJson(),IdentitySessionStore.ActorStamp.class)))
                    return denied(IdentityErrorCode.INVALID_REAUTHENTICATION);
            }catch(BizException invalidActor){return denied(IdentityErrorCode.INVALID_REAUTHENTICATION);}
        }
        char[] candidate=input.code().toCharArray();boolean valid;
        try{valid=secrets.matchesFingerprint(reference(row),"email-challenge-code",candidate,row.getCodeFingerprint());}
        finally{Arrays.fill(candidate,'\0');}
        if(!valid){
            row.setFailures(row.getFailures()+1);
            if(row.getFailures()>=settings.maximumFailures())terminal(budget,row,"FAILED");else challenges.updateById(row);
            return denied(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);
        }
        String grant=token();row.setGrantHash(hash(grant));row.setStatus("VERIFIED");row.setCodeFingerprint(null);
        Instant expires=clock.now().plus(settings.grantLifetime()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);row.setGrantExpiresAt(expires.isAfter(row.getExpiresAt())?row.getExpiresAt():expires);challenges.updateById(row);
        return new Result(grant,row.getGrantExpiresAt(),null);
    }
    /** The callback may perform same-resource identity writes only, never network operations or independent commits. */
    @Transactional(propagation=Propagation.MANDATORY)
    public <T>T consume(Use input,Function<Granted,T> mutation){
        IdentityAuthority.worker();if(input==null || input.purpose()==null || !token(input.grant()) || !token(input.proof()) || !token(input.loginHandle()) || mutation==null)throw invalid();
        var budget=budgets.lock();var row=challenges.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().eq(IdentityEmailChallengeEntity::getGrantHash,hash(input.grant())).last("FOR UPDATE"));
        if(row==null || !proof(row,input.proof(),input.purpose(),input.origin()) || !row.getStatus().equals("VERIFIED") || !row.getGrantExpiresAt().isAfter(clock.now()))throw invalid();
        var snapshot=current(row,input.loginHandle());String address;
        try(var value=secrets.decrypt(reference(row),"email-challenge-target",row.getTargetEnvelope())){
            char[] chars=value.copy();try{address=new String(chars);}finally{Arrays.fill(chars,'\0');}
        }
        T result=mutation.apply(new Granted(row.getId(),snapshot,address,row.getTargetIndex(),row.getSubjectId(),row.getSubjectGeneration()));
        terminal(budget,row,"CONSUMED");return result;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public int maintain(){IdentityAuthority.worker();var budget=budgets.existing();return budget==null?0:cleanup(budget);}
    private IdentityAuthority.Snapshot current(IdentityEmailChallengeEntity row,String handle){
        var snapshot=json.readValue(row.getSnapshotJson(),IdentityAuthority.Snapshot.class);var live=authority.match(snapshot);
        if(!live.client().getLoginHandle().equals(handle))throw new WrongClient();
        if(!live.realm().getPasswordEnabled()
                || row.getPurpose().equals(Purpose.REGISTRATION.name()) && !live.realm().getRegistrationEnabled())throw invalid();
        var configuration=configurations.selectOne(new LambdaQueryWrapper<IdentityEmailConfigurationEntity>().eq(IdentityEmailConfigurationEntity::getId,row.getEmailConfigurationId()).last("FOR SHARE"));
        if(configuration==null || !configuration.getEnabled() || !configuration.getVersion().equals(row.getEmailConfigurationVersion())
                || configuration.getClientId()!=snapshot.clientId() || !configuration.getLanguage().equals(row.getLanguage()))throw invalid();
        if(row.getSubjectId()!=null)subject(row.getSubjectId(),row.getSubjectGeneration(),snapshot.realmId());return snapshot;
    }
    private void subject(long id,Long generation,long realm){
        var subject=subjects.selectOne(new LambdaQueryWrapper<IdentitySubjectEntity>().eq(IdentitySubjectEntity::getId,id).last("FOR SHARE"));
        if(subject==null || !subject.getEnabled() || subject.getRealmId()!=realm || !subject.getSecurityVersion().equals(generation))throw invalid();
    }
    private void origin(IdentityAuthority.Snapshot snapshot,IdentityClientRevisionEntity configuration,String origin){
        if(snapshot.platform().equals("WEB")){
            if(origin==null || !json.readValue(configuration.getOriginsJson(),new TypeReference<List<String>>(){}).contains(origin))throw invalid();
        }else if(origin!=null)throw invalid();
    }
    private boolean proof(IdentityEmailChallengeEntity row,String proof,Purpose purpose,String origin){
        return row.getPurpose().equals(purpose.name()) && Objects.equals(row.getOrigin(),origin)
                && MessageDigest.isEqual(row.getProofHash().getBytes(StandardCharsets.US_ASCII),hash(proof).getBytes(StandardCharsets.US_ASCII));
    }
    private int cleanup(IdentityEmailBudgetEntity budget){
        int count=0;var expired=challenges.selectList(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().and(q->q
                .in(IdentityEmailChallengeEntity::getStatus,List.of("PREPARED","PENDING")).le(IdentityEmailChallengeEntity::getExpiresAt,clock.now())
                .or(v->v.eq(IdentityEmailChallengeEntity::getStatus,"VERIFIED").le(IdentityEmailChallengeEntity::getGrantExpiresAt,clock.now())))
                .orderByAsc(IdentityEmailChallengeEntity::getId).last("LIMIT 100 FOR UPDATE"));
        for(var row:expired){terminal(budget,row,"EXPIRED");count++;}
        var old=challenges.selectList(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().in(IdentityEmailChallengeEntity::getStatus,List.of("FAILED","EXPIRED","CONSUMED"))
                .le(IdentityEmailChallengeEntity::getRetainUntil,clock.now()).orderByAsc(IdentityEmailChallengeEntity::getId).last("LIMIT 100 FOR UPDATE"));
        for(var row:old){challenges.purge(row.getId());budgets.purged(budget);count++;}return count+budgets.cleanup();
    }
    private void terminal(IdentityEmailBudgetEntity budget,IdentityEmailChallengeEntity row,String status){
        if(!Set.of("PREPARED","PENDING","VERIFIED").contains(row.getStatus()))throw new IllegalStateException("Email challenge is already terminal");
        row.setStatus(status);row.setTargetEnvelope(null);row.setCodeFingerprint(null);row.setGrantHash(null);budgets.terminal(budget);challenges.updateById(row);
    }
    static String scene(Purpose purpose){return switch(purpose){case REGISTRATION->"registration";case PASSWORD_RESET->"password-reset";case EMAIL_BINDING->"email-binding";};}
    static TenantConfigurationRef reference(IdentityEmailChallengeEntity row){return new TenantConfigurationRef(row.getTenantId(),"identity",row.getId(),1,1);}
    private String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private boolean token(String value){return value!=null && value.matches("[A-Za-z0-9_-]{43}");}
    private String hash(String value){return IdentitySessionStore.hashToken(value);}
    private Result denied(IdentityErrorCode error){return new Result(null,null,error);}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);}
}
