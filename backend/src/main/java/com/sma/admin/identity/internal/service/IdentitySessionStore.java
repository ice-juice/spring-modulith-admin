package com.sma.admin.identity.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.*;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Internal coordinator contract, not an alternative to the persisted login transaction or HTTP security chain. */
@Service @EnableConfigurationProperties(IdentitySessionProperties.class)
public class IdentitySessionStore {
    public enum AuthenticationMethod { GOOGLE, PASSWORD, EMAIL_VERIFIED }
    public record ActorStamp(IdentityAuthority.Snapshot authority,long subjectGeneration,long sessionId,String loginHandle) {}
    public record Issued(long subjectId,long realmId,long clientId,long sessionId,String application,String mode,
                         String accessToken,String refreshToken,String csrfToken,Instant accessExpiresAt,Instant refreshExpiresAt){
        @Override public String toString(){return "ApplicationSession[REDACTED]";}
    }
    public record Access(AppUserPrincipal principal,String mode,String csrfHash,String loginHandle,Instant accessExpiresAt,Instant refreshExpiresAt,String authenticationMethod,Instant authenticatedAt) {
        @Override public String toString(){return "ApplicationAccess[REDACTED]";}
    }
    /** Only this failure commits the family revocation instead of rolling it back. */
    public static class RefreshReplay extends BizException {public RefreshReplay(){super(IdentityErrorCode.INVALID_SESSION);}}
    private final IdentityAuthority authority;private final IdentitySubjectMapper subjects;private final IdentityExternalMapper external;
    private final IdentitySessionMapper sessions;private final IdentityRefreshMapper refreshes;private final IdentityClock clock;
    private final IdentitySessionProperties settings;private final ApplicationEventPublisher events;
    private final SecureRandom random=new SecureRandom();
    public IdentitySessionStore(IdentityAuthority authority,IdentitySubjectMapper subjects,IdentityExternalMapper external,
                                IdentitySessionMapper sessions,IdentityRefreshMapper refreshes,IdentityClock clock,
                                IdentitySessionProperties settings,ApplicationEventPublisher events){
        this.authority=authority;this.subjects=subjects;this.external=external;this.sessions=sessions;this.refreshes=refreshes;this.clock=clock;this.settings=settings;this.events=events;
    }
    @Transactional public Issued google(IdentityAuthority.Snapshot expected,GoogleIdTokenVerifier.Verified verified){
        IdentityAuthority.worker();var live=authority.match(expected);
        if(!live.configuration().getGoogleEnabled() || verified==null || !"https://accounts.google.com".equals(verified.issuer())
                || verified.subject()==null || !verified.subject().matches("[!-~]{1,255}"))throw invalid();
        var binding=external.selectOne(new LambdaQueryWrapper<IdentityExternalEntity>().eq(IdentityExternalEntity::getRealmId,expected.realmId())
                .eq(IdentityExternalEntity::getIssuer,verified.issuer()).eq(IdentityExternalEntity::getExternalSubject,verified.subject()).last("FOR UPDATE"));
        IdentitySubjectEntity subject;
        if(binding==null){
            if(external.history(expected.realmId(),verified.issuer(),verified.subject())!=null)throw new BizException(IdentityErrorCode.REVOKED_IDENTITY_LINK);
            if(!live.realm().getRegistrationEnabled())throw new BizException(IdentityErrorCode.REGISTRATION_DISABLED);
            subject=new IdentitySubjectEntity();subject.setRealmId(expected.realmId());subject.setEnabled(true);subject.setSecurityVersion(1L);subjects.insert(subject);
            binding=new IdentityExternalEntity();binding.setRealmId(expected.realmId());binding.setSubjectId(subject.getId());binding.setIssuer(verified.issuer());binding.setExternalSubject(verified.subject());external.insert(binding);
            events.publishEvent(new ApplicationSubjectCreated(expected.tenantId(),expected.realmId(),expected.clientId(),expected.application(),subject.getId(),"GOOGLE"));
        }else subject=subject(binding.getSubjectId(),true);
        return issueSubject(live,subject,AuthenticationMethod.GOOGLE);
    }
    /** Only an identity coordinator after atomic proof verification may call this; not a business session-issuance API. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Issued verifiedSubject(IdentityAuthority.Snapshot expected,long subjectId,AuthenticationMethod method){
        IdentityAuthority.worker();if(method==null || method==AuthenticationMethod.GOOGLE)throw invalid();var live=authority.match(expected);
        if(!live.realm().getPasswordEnabled())throw invalid();return issueSubject(live,subject(subjectId,true),method);
    }
    private Issued issueSubject(IdentityAuthority.Loaded live,IdentitySubjectEntity subject,AuthenticationMethod method){
        var expected=live.snapshot();if(!subject.getEnabled() || subject.getRealmId()!=expected.realmId())throw invalid();
        sessions.update(new LambdaUpdateWrapper<IdentitySessionEntity>().eq(IdentitySessionEntity::getSubjectId,subject.getId()).eq(IdentitySessionEntity::getStatus,"ACTIVE")
                .and(q->q.ne(IdentitySessionEntity::getTenantGeneration,expected.tenantGeneration()).or().ne(IdentitySessionEntity::getRealmGeneration,expected.realmGeneration())
                        .or().ne(IdentitySessionEntity::getSubjectGeneration,subject.getSecurityVersion())
                        .or(c->c.eq(IdentitySessionEntity::getClientId,expected.clientId()).ne(IdentitySessionEntity::getClientGeneration,expected.clientGeneration())))
                .set(IdentitySessionEntity::getStatus,"REVOKED").set(IdentitySessionEntity::getRevokedAt,clock.now()).set(IdentitySessionEntity::getRevocationReason,"GENERATION_CHANGED"));
        // Current read under the subject lock, including when the caller already owns an older repeatable-read snapshot.
        long count=sessions.selectList(new LambdaQueryWrapper<IdentitySessionEntity>().select(IdentitySessionEntity::getId).eq(IdentitySessionEntity::getSubjectId,subject.getId())
                .eq(IdentitySessionEntity::getStatus,"ACTIVE").gt(IdentitySessionEntity::getRefreshExpiresAt,clock.now()).last("LIMIT 100 FOR UPDATE")).size();
        if(count>=settings.maximumPerSubject())throw new BizException(IdentityErrorCode.SESSION_LIMIT);
        var row=new IdentitySessionEntity();row.setId(IdWorker.getId());row.setTenantId(expected.tenantId());row.setSubjectId(subject.getId());row.setRealmId(expected.realmId());
        row.setClientId(expected.clientId());row.setApplication(expected.application());row.setMode(mode(expected.platform()));row.setStatus("ACTIVE");
        row.setAuthenticationMethod(method.name());row.setAuthenticatedAt(clock.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        row.setTenantGeneration(expected.tenantGeneration());row.setRealmGeneration(expected.realmGeneration());row.setClientGeneration(expected.clientGeneration());row.setSubjectGeneration(subject.getSecurityVersion());
        row.setRefreshExpiresAt(clock.now().plusSeconds(live.configuration().getRefreshTokenSeconds()));
        return issue(row,live.configuration().getAccessTokenSeconds(),true);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public Access authenticate(String raw,String loginHandle,String expectedMode){
        IdentityAuthority.worker();String hash=hashToken(raw);
        var found=sessions.selectOne(new LambdaQueryWrapper<IdentitySessionEntity>().eq(IdentitySessionEntity::getAccessHash,hash));if(found==null)throw invalid();
        var live=authority.current(found.getClientId(),false);var subject=subject(found.getSubjectId(),false);
        var row=session(found.getId(),false);valid(row,subject,live,loginHandle,expectedMode);
        if(!row.getAccessHash().equals(hash) || !row.getAccessExpiresAt().isAfter(clock.now()))throw invalid();
        return new Access(new AppUserPrincipal(row.getSubjectId(),row.getTenantId(),row.getRealmId(),row.getClientId(),row.getApplication(),row.getId()),
                row.getMode(),row.getCsrfHash(),live.client().getLoginHandle(),row.getAccessExpiresAt(),row.getRefreshExpiresAt(),row.getAuthenticationMethod(),row.getAuthenticatedAt());
    }
    /** Pure authoritative actor read. The actor must come from the HTTP security context, never a body DTO. */
    @Transactional(propagation=Propagation.MANDATORY,noRollbackFor=BizException.class)
    public ActorStamp currentActor(AppUserPrincipal actor){
        IdentityAuthority.worker();if(actor==null)throw invalid();ActorContext.requireTenant(actor.tenantId());
        var first=sessions.selectById(actor.sessionId());if(first==null)throw invalid();
        var live=authority.current(first.getClientId(),true);var subject=subject(first.getSubjectId(),true);var row=session(first.getId(),true);
        valid(row,subject,live,live.client().getLoginHandle(),mode(live.snapshot().platform()));
        if(row.getSubjectId()!=actor.subjectId() || row.getRealmId()!=actor.realmId() || row.getClientId()!=actor.clientId() || !row.getApplication().equals(actor.audience())
                || !row.getAccessExpiresAt().isAfter(clock.now()))throw invalid();
        return new ActorStamp(live.snapshot(),subject.getSecurityVersion(),row.getId(),live.client().getLoginHandle());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,noRollbackFor=RefreshReplay.class,isolation=Isolation.READ_COMMITTED)
    public Issued refresh(String raw,String loginHandle,String expectedMode,String csrf){
        IdentityAuthority.worker();String hash=hashToken(raw);
        var found=refreshes.selectOne(new LambdaQueryWrapper<IdentityRefreshEntity>().eq(IdentityRefreshEntity::getTokenHash,hash));if(found==null)throw invalid();
        var prior=sessions.selectById(found.getSessionId());if(prior==null)throw invalid();
        var live=authority.current(prior.getClientId(),true);var subject=subject(prior.getSubjectId(),true);var row=session(prior.getId(),true);
        valid(row,subject,live,loginHandle,expectedMode);
        var token=refreshes.selectOne(new LambdaQueryWrapper<IdentityRefreshEntity>().eq(IdentityRefreshEntity::getTokenHash,hash).last("FOR UPDATE"));
        if(token==null || !token.getSessionId().equals(row.getId()) || !token.getExpiresAt().isAfter(clock.now()))throw invalid();
        if(row.getMode().equals("WEB") && !csrfMatches(csrf,token.getCsrfHash()))throw invalid();
        if(token.getConsumedAt()!=null){revoke(row,"REFRESH_REUSE");throw new RefreshReplay();}
        token.setConsumedAt(clock.now());refreshes.updateById(token);
        return issue(row,live.configuration().getAccessTokenSeconds(),false);
    }
    @Transactional public void logout(){
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal))throw new AccessDeniedException("Application user required");
        ActorContext.requireTenant(principal.tenantId());var row=session(principal.sessionId(),true);
        if(row.getSubjectId()!=principal.subjectId() || row.getClientId()!=principal.clientId() || row.getRealmId()!=principal.realmId())throw invalid();
        if(row.getStatus().equals("ACTIVE"))revoke(row,"LOGOUT");
    }
    /** Called by the identity password/reset coordinator inside its transaction; never expose an arbitrary subject route. */
    @Transactional public void revokeSubject(long id){
        IdentityAuthority.worker();var subject=subject(id,true);subject.setSecurityVersion(Math.addExact(subject.getSecurityVersion(),1));subjects.updateById(subject);
        sessions.update(new LambdaUpdateWrapper<IdentitySessionEntity>().eq(IdentitySessionEntity::getSubjectId,id).eq(IdentitySessionEntity::getStatus,"ACTIVE")
                .set(IdentitySessionEntity::getStatus,"REVOKED").set(IdentitySessionEntity::getRevokedAt,clock.now()).set(IdentitySessionEntity::getRevocationReason,"SUBJECT_CREDENTIAL_CHANGE"));
    }
    private Issued issue(IdentitySessionEntity row,int accessSeconds,boolean insert){
        String access=token(),refresh=token(),csrf=row.getMode().equals("WEB")?token():null;
        row.setAccessHash(hashToken(access));row.setCsrfHash(csrf==null?null:hashToken(csrf));row.setAccessExpiresAt(clock.now().plusSeconds(accessSeconds));
        if(row.getAccessExpiresAt().isAfter(row.getRefreshExpiresAt()))row.setAccessExpiresAt(row.getRefreshExpiresAt());
        if(insert)sessions.insert(row);else sessions.updateById(row);
        var next=new IdentityRefreshEntity();next.setSessionId(row.getId());next.setTokenHash(hashToken(refresh));next.setCsrfHash(row.getCsrfHash());next.setExpiresAt(row.getRefreshExpiresAt());refreshes.insert(next);
        return new Issued(row.getSubjectId(),row.getRealmId(),row.getClientId(),row.getId(),row.getApplication(),row.getMode(),access,refresh,csrf,row.getAccessExpiresAt(),row.getRefreshExpiresAt());
    }
    private void valid(IdentitySessionEntity row,IdentitySubjectEntity subject,IdentityAuthority.Loaded live,String handle,String expectedMode){
        var snapshot=live.snapshot();
        if(!row.getStatus().equals("ACTIVE") || !row.getRefreshExpiresAt().isAfter(clock.now()) || !subject.getEnabled()
                || !row.getSubjectId().equals(subject.getId()) || !row.getRealmId().equals(subject.getRealmId()) || row.getRealmId()!=snapshot.realmId()
                || row.getClientId()!=snapshot.clientId() || !row.getApplication().equals(snapshot.application()) || !live.client().getLoginHandle().equals(handle)
                || !row.getMode().equals(expectedMode) || !row.getMode().equals(mode(snapshot.platform()))
                || row.getTenantGeneration()!=snapshot.tenantGeneration() || row.getRealmGeneration()!=snapshot.realmGeneration()
                || row.getClientGeneration()!=snapshot.clientGeneration() || !row.getSubjectGeneration().equals(subject.getSecurityVersion()))throw invalid();
    }
    private IdentitySubjectEntity subject(long id,boolean exclusive){
        var row=subjects.selectOne(new LambdaQueryWrapper<IdentitySubjectEntity>().eq(IdentitySubjectEntity::getId,id).last(exclusive?"FOR UPDATE":"FOR SHARE"));
        if(row==null)throw invalid();return row;
    }
    private IdentitySessionEntity session(long id,boolean exclusive){
        var row=sessions.selectOne(new LambdaQueryWrapper<IdentitySessionEntity>().eq(IdentitySessionEntity::getId,id).last(exclusive?"FOR UPDATE":"FOR SHARE"));
        if(row==null)throw invalid();return row;
    }
    private void revoke(IdentitySessionEntity row,String reason){row.setStatus("REVOKED");row.setRevokedAt(clock.now());row.setRevocationReason(reason);sessions.updateById(row);}
    private String token(){byte[] value=new byte[32];random.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    static String hashToken(String raw){
        if(raw==null || !raw.matches("[A-Za-z0-9_-]{43}"))throw new BizException(IdentityErrorCode.INVALID_SESSION);
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static boolean csrfMatches(String raw,String hash){
        if(raw==null || !raw.matches("[A-Za-z0-9_-]{43}") || hash==null)return false;
        return MessageDigest.isEqual(hashToken(raw).getBytes(StandardCharsets.US_ASCII),hash.getBytes(StandardCharsets.US_ASCII));
    }
    private String mode(String platform){return platform.equals("WEB")?"WEB":"NATIVE";}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_SESSION);}
}
