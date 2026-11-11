package com.sma.admin.auth.internal.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.*;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.iam.*;

@Service
public class SecurityPasswordService implements SecurityPasswordVerification {
    private final AccountCredentialGuard credentials;private final AccountSecurityStore security;private final SecuritySessionGuard sessions;
    private final SecurityReauthentication reauth;private final SecurityPasswordMapper passwords;private final SecurityPasswordHash hash;
    private final SecurityRevocation revocation;private final SecurityRateLimit limits;private final SecurityChallenges clock;
    public SecurityPasswordService(AccountCredentialGuard credentials,AccountSecurityStore security,SecuritySessionGuard sessions,SecurityReauthentication reauth,
            SecurityPasswordMapper passwords,SecurityPasswordHash hash,SecurityRevocation revocation,SecurityRateLimit limits,SecurityChallenges clock){
        this.credentials=credentials;this.security=security;this.sessions=sessions;this.reauth=reauth;this.passwords=passwords;this.hash=hash;this.revocation=revocation;this.limits=limits;this.clock=clock;
    }
    @Transactional public void set(long sessionId,String loginPassword,String code,String candidate,String confirmation,String ip){write("SET",sessionId,loginPassword,code,null,candidate,confirmation,ip);}
    @Transactional public void change(long sessionId,String loginPassword,String code,String oldPassword,String candidate,String confirmation,String ip){write("CHANGE",sessionId,loginPassword,code,oldPassword,candidate,confirmation,ip);}
    @Transactional public void reset(long sessionId,String loginPassword,String code,String candidate,String confirmation,String ip){write("RESET",sessionId,loginPassword,code,null,candidate,confirmation,ip);}

    private void write(String mode,long sessionId,String loginPassword,String code,String oldPassword,String candidate,String confirmation,String ip){
        CurrentUser.require();limits.claim("SECURITY_WRITE_IP",ip,60,Duration.ofMinutes(15));
        limits.claim("SECURITY_WRITE_ACCOUNT",CurrentUser.portal()+":"+CurrentUser.userId(),10,Duration.ofMinutes(15));
        if(candidate==null||!candidate.matches("[A-Za-z0-9]{6,8}")||!candidate.matches(".*[A-Za-z].*")||!candidate.matches(".*[0-9].*")||!candidate.equals(confirmation))throw new BizException(AuthErrorCode.SECURITY_PASSWORD_FORMAT);
        credentials.lockOwnAccount();var state=security.lock(CurrentUser.userId(),CurrentUser.portal());var user=sessions.require(sessionId);
        var row=lockedPassword(user.id(),user.portal());
        if((mode.equals("SET")&&row!=null)||(!mode.equals("SET")&&row==null))throw new BizException(AuthErrorCode.SECURITY_STATE_CHANGED);
        reauth.verify(user,loginPassword,code,ip,mode.equals("RESET"));
        if(mode.equals("CHANGE"))verifyCandidate(row,oldPassword,user.id(),user.portal());
        if(credentials.matchesOwnCurrentPassword(candidate))throw new BizException(IamErrorCode.PASSWORD_CONFLICT);
        if(row==null){row=new SecurityPasswordEntity();row.setUserId(user.id());row.setPortal(user.portal());row.setCredentialVersion(0L);}
        String key=hash.currentKey();row.setPasswordHash(hash.encode(candidate,key));row.setPepperKeyId(key);
        row.setCredentialVersion(Math.addExact(row.getCredentialVersion(),1));row.setChangedAt(clock.now());
        if(row.getId()==null)passwords.insert(row);else passwords.updateById(row);
        revocation.changed(state);
    }
    @Override @Transactional
    public void verifyForCurrentSession(long sessionId,String candidate){
        CurrentUser.require();security.lock(CurrentUser.userId(),CurrentUser.portal());var user=sessions.require(sessionId);
        var row=lockedPassword(user.id(),user.portal());verifyCandidate(row,candidate,user.id(),user.portal());
        // Lazy pepper migration keeps old keys readable until every affected account has migrated.
        String active=hash.currentKey();
        if(!active.equals(row.getPepperKeyId())){row.setPasswordHash(hash.encode(candidate,active));row.setPepperKeyId(active);passwords.updateById(row);}
    }
    private SecurityPasswordEntity lockedPassword(long userId,String portal){return passwords.selectOne(new LambdaQueryWrapper<SecurityPasswordEntity>().eq(SecurityPasswordEntity::getUserId,userId).eq(SecurityPasswordEntity::getPortal,portal).last("FOR UPDATE"));}
    private void verifyCandidate(SecurityPasswordEntity row,String candidate,long userId,String portal){
        limits.claim("SECURITY_PASSWORD_ACCOUNT",portal+":"+userId,5,Duration.ofMinutes(15));
        if(row==null||candidate==null||!candidate.matches("[A-Za-z0-9]{6,8}")||!hash.matches(candidate,row.getPasswordHash(),row.getPepperKeyId()))throw new BizException(AuthErrorCode.SECURITY_PASSWORD_INVALID);
    }
}
