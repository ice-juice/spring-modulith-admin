package com.sma.admin.auth.internal.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.auth.AuthErrorCode;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.CurrentUser;

@Service
public class TotpEnrollmentService {
    private final AccountSecurityStore security;private final SecuritySessionGuard sessions;private final SecurityReauthentication reauth;
    private final SecurityChallenges challenges;private final TotpCodes codes;private final SecuritySecrets secrets;
    private final TotpCredentialMapper totps;private final SecurityRevocation revocation;private final AccountSecurityProperties settings;private final SecurityRateLimit limits;
    public TotpEnrollmentService(AccountSecurityStore security,SecuritySessionGuard sessions,SecurityReauthentication reauth,SecurityChallenges challenges,TotpCodes codes,SecuritySecrets secrets,TotpCredentialMapper totps,SecurityRevocation revocation,AccountSecurityProperties settings,SecurityRateLimit limits){
        this.security=security;this.sessions=sessions;this.reauth=reauth;this.challenges=challenges;this.codes=codes;this.secrets=secrets;this.totps=totps;this.revocation=revocation;this.settings=settings;this.limits=limits;
    }
    public record Enrollment(String challengeToken,String secret,String uri,Instant expiresAt,boolean replacing){}
    @Transactional public Enrollment begin(long sessionId,String password,String code,String ip){
        var state=security.lock(CurrentUser.userId(),CurrentUser.portal());var user=sessions.require(sessionId);
        reauth.verify(user,password,code,ip,false);
        var row=security.lockedTotp(user.id(),user.portal());boolean replacing=row!=null&&row.getEnabled();
        if(row==null){row=new TotpCredentialEntity();row.setUserId(user.id());row.setPortal(user.portal());row.setEnabled(false);}
        String secret=codes.newSecret();Instant expires=challenges.now().plusSeconds(600);
        var challenge=new SecurityChallengeEntity();challenge.setUserId(user.id());challenge.setPortal(user.portal());challenge.setPurpose("TOTP_ENROLL");
        challenge.setSourceSessionId(sessionId);challenge.setAccountCredentialVersion(user.credentialVersion());challenge.setSecurityVersion(state.getSecurityVersion());
        challenge.setExpiresAt(expires);challenge.setSingleTerminal(false);challenge.setSessionTtlSeconds(28800L);
        String token=challenges.insert(challenge);
        row.setPendingSecretCiphertext(secrets.encrypt(secret,SessionIssuer.context(user)+":pending"));row.setPendingChallengeId(challenge.getId());row.setPendingExpiresAt(expires);
        if(row.getId()==null)totps.insert(row);else totps.updateById(row);
        String label=settings.issuer()+":"+user.username();
        String uri="otpauth://totp/"+URLEncoder.encode(label,StandardCharsets.UTF_8).replace("+","%20")+"?secret="+secret+"&issuer="+URLEncoder.encode(settings.issuer(),StandardCharsets.UTF_8).replace("+","%20")+"&algorithm=SHA1&digits=6&period=30";
        return new Enrollment(token,secret,uri,expires,replacing);
    }
    /** Called by a non-transactional facade after reserving the challenge attempt. */
    @Transactional public void confirm(long sessionId,String token,String code){
        var state=security.lock(CurrentUser.userId(),CurrentUser.portal());var user=sessions.require(sessionId);
        var challenge=challenges.lock(token,"TOTP_ENROLL");var row=security.lockedTotp(user.id(),user.portal());
        if(challenge.getUserId()!=user.id().longValue()||!challenge.getPortal().equals(user.portal())||challenge.getSourceSessionId()==null||challenge.getSourceSessionId()!=sessionId
                ||challenge.getAccountCredentialVersion()!=user.credentialVersion()||challenge.getSecurityVersion().longValue()!=state.getSecurityVersion()
                ||row==null||!challenge.getId().equals(row.getPendingChallengeId())||row.getPendingExpiresAt()==null||!row.getPendingExpiresAt().isAfter(challenges.now()))throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
        String secret=secrets.decrypt(row.getPendingSecretCiphertext(),SessionIssuer.context(user)+":pending");
        long step=codes.acceptedStep(secret,code,challenges.now(),null);if(step<0)throw new BizException(AuthErrorCode.INVALID_TOTP);
        row.setSecretCiphertext(secrets.encrypt(secret,SessionIssuer.context(user)));row.setEnabled(true);row.setEnabledAt(challenges.now());row.setLastAcceptedStep(step);
        clearPending(row);totps.updateById(row);challenges.consume(challenge);revocation.changed(state);
    }
    @Transactional public void cancel(long sessionId,String token){
        security.lock(CurrentUser.userId(),CurrentUser.portal());var user=sessions.require(sessionId);var challenge=challenges.lock(token,"TOTP_ENROLL");
        var row=security.lockedTotp(user.id(),user.portal());
        if(challenge.getSourceSessionId()==null||challenge.getSourceSessionId()!=sessionId||challenge.getUserId()!=user.id().longValue()
                ||row==null||!challenge.getId().equals(row.getPendingChallengeId()))throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
        clearPending(row);totps.updateById(row);challenges.consume(challenge);
    }
    @Transactional public void disable(long sessionId,String password,String code,String ip){
        var state=security.lock(CurrentUser.userId(),CurrentUser.portal());var user=sessions.require(sessionId);reauth.verify(user,password,code,ip,true);
        var row=security.lockedTotp(user.id(),user.portal());row.setEnabled(false);row.setSecretCiphertext(null);row.setLastAcceptedStep(null);row.setEnabledAt(null);
        clearPending(row);totps.updateById(row);revocation.changed(state);
    }
    private void clearPending(TotpCredentialEntity row){row.setPendingSecretCiphertext(null);row.setPendingChallengeId(null);row.setPendingExpiresAt(null);}
}
