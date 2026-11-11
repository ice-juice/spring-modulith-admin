package com.sma.admin.auth.internal.service;

import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.*;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.BizException;
import com.sma.admin.iam.AccountView;
import com.sma.admin.iam.AccountQuery;

@Service
public class SessionIssuer {
    private final SessionMapper mapper;
    private final LoginEvents events;
    private final org.springframework.context.ApplicationEventPublisher publisher;
    private final SessionMaintenance maintenance;
    private final AccountSecurityStore security;
    private final SecurityChallenges challenges;
    private final TotpCredentialMapper totps;
    private final SecuritySecrets secrets;
    private final TotpCodes codes;
    private final AccountQuery accounts;
    private final TenantAuthenticationGuard tenants;
    public SessionIssuer(SessionMapper mapper,LoginEvents events,org.springframework.context.ApplicationEventPublisher publisher,
            SessionMaintenance maintenance,AccountSecurityStore security,SecurityChallenges challenges,TotpCredentialMapper totps,
            SecuritySecrets secrets,TotpCodes codes,AccountQuery accounts,TenantAuthenticationGuard tenants) {
        this.mapper=mapper;this.events=events;this.publisher=publisher;this.maintenance=maintenance;this.security=security;
        this.challenges=challenges;this.totps=totps;this.secrets=secrets;this.codes=codes;this.accounts=accounts;this.tenants=tenants;
    }
    @Transactional
    public SessionService.LoginResult begin(AccountView user,boolean singleTerminal,Duration ttl,String ip,String agent,boolean locked) {
        long generation=tenants.capture(user.tenantId(),user.portal());
        maintenance.seen();
        var state=security.lock(user.id(),user.portal());var totp=security.lockedTotp(user.id(),user.portal());
        if(totp==null||!totp.getEnabled())return create(user,state,singleTerminal,ttl,ip,agent,locked,false,generation);
        var row=new SecurityChallengeEntity();row.setUserId(user.id());row.setPortal(user.portal());row.setPurpose("LOGIN_TOTP");
        row.setAccountCredentialVersion(user.credentialVersion());row.setSecurityVersion(state.getSecurityVersion());
        row.setTenantSecurityGeneration(generation);
        row.setExpiresAt(challenges.now().plusSeconds(180));row.setSingleTerminal(singleTerminal);row.setSessionTtlSeconds(Math.min(ttl.toSeconds(),28800));
        String token=challenges.insert(row);return new SessionService.LoginResult("MFA_REQUIRED",null,row.getExpiresAt(),token,false);
    }
    /** Password-only internal entry cannot issue a session for a bound account. */
    @Transactional public SessionService.LoginResult issue(AccountView user,boolean singleTerminal,Duration ttl,String ip,String agent){return issue(user,singleTerminal,ttl,ip,agent,false);}
    @Transactional public SessionService.LoginResult issue(AccountView user,boolean singleTerminal,Duration ttl,String ip,String agent,boolean locked){
        long generation=tenants.capture(user.tenantId(),user.portal());
        var state=security.lock(user.id(),user.portal());var totp=security.lockedTotp(user.id(),user.portal());
        if(totp!=null&&totp.getEnabled())throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
        return create(user,state,singleTerminal,ttl,ip,agent,locked,false,generation);
    }
    @Transactional
    public SessionService.LoginResult complete(String token,SecurityChallengeEntity reservation,String code,String ip,String agent){
        var state=security.lock(reservation.getUserId(),reservation.getPortal());var challenge=challenges.lock(token,"LOGIN_TOTP");
        if(challenge.getUserId().longValue()!=reservation.getUserId()||!challenge.getPortal().equals(reservation.getPortal()))throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
        var user=accounts.findById(challenge.getUserId()).filter(u->u.enabled()&&u.portal().equals(challenge.getPortal())
                &&u.credentialVersion()==challenge.getAccountCredentialVersion()).orElseThrow(()->new BizException(AuthErrorCode.INVALID_CHALLENGE));
        if(state.getSecurityVersion().longValue()!=challenge.getSecurityVersion())throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
        var totp=security.lockedTotp(user.id(),user.portal());if(totp==null||!totp.getEnabled())throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
        long step=codes.acceptedStep(secrets.decrypt(totp.getSecretCiphertext(),context(user)),code,challenges.now(),totp.getLastAcceptedStep());
        if(step<0)throw new BizException(AuthErrorCode.INVALID_TOTP);
        totp.setLastAcceptedStep(step);totps.updateById(totp);challenges.consume(challenge);
        return create(user,state,challenge.getSingleTerminal(),Duration.ofSeconds(challenge.getSessionTtlSeconds()),ip,agent,false,true,challenge.getTenantSecurityGeneration());
    }
    static String context(AccountView user){return user.tenantId()+":"+user.id()+":"+user.portal()+":totp";}
    private SessionService.LoginResult create(AccountView user,UserSecurityEntity state,boolean singleTerminal,Duration ttl,String ip,String agent,boolean locked,boolean mfa,long generation){
        tenants.require(user.tenantId(),user.portal(),generation);
        maintenance.seen();
        if(singleTerminal){
            var query=new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getUserId,user.id()).eq(SessionEntity::getPortal,user.portal());
            var previous=mapper.selectList(query);mapper.delete(query);
            previous.forEach(session->publisher.publishEvent(new SessionEndedEvent(user.tenantId(),user.id(),session.getId(),"REPLACED")));
        }
        String token=Tokens.create();var now=challenges.now();
        var row=new SessionEntity();row.setUserId(user.id());row.setPortal(user.portal());row.setTokenHash(Tokens.hash(token));
        row.setTenantSecurityGeneration(generation);
        row.setExpiresAt(now.plus(ttl));row.setLastSeenAt(now);row.setIp(ip);row.setUserAgent(agent==null?null:agent.substring(0,Math.min(agent.length(),512)));row.setLocked(locked);
        row.setCredentialVersion(user.credentialVersion());row.setSecurityVersion(state.getSecurityVersion());row.setAuthenticationMethod(mfa?"PASSWORD_TOTP":"PASSWORD");
        row.setPasswordVerifiedAt(now);row.setMfaVerifiedAt(mfa?now:null);mapper.insert(row);
        events.record(user.id(),user.username(),user.portal(),"SUCCESS",ip,agent);
        return new SessionService.LoginResult("AUTHENTICATED",token,row.getExpiresAt(),null,!mfa);
    }
}
