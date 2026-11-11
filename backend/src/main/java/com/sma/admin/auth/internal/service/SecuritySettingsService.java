package com.sma.admin.auth.internal.service;
import java.time.Instant;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.internal.persistence.*;

@Service
public class SecuritySettingsService {
    private final SecuritySessionGuard sessions;private final AccountSecurityStore security;private final SecurityPasswordMapper passwords;private final SecuritySecrets secrets;
    public SecuritySettingsService(SecuritySessionGuard sessions,AccountSecurityStore security,SecurityPasswordMapper passwords,SecuritySecrets secrets){this.sessions=sessions;this.security=security;this.passwords=passwords;this.secrets=secrets;}
    public record Settings(boolean totpEnabled,boolean totpPending,Instant totpEnabledAt,boolean securityPasswordSet,Instant securityPasswordChangedAt,boolean setupAvailable){}
    public Settings get(long sessionId){
        var user=sessions.require(sessionId);var totp=security.totp(user.id(),user.portal());
        var password=passwords.selectOne(new LambdaQueryWrapper<SecurityPasswordEntity>().eq(SecurityPasswordEntity::getUserId,user.id()).eq(SecurityPasswordEntity::getPortal,user.portal()));
        return new Settings(totp!=null&&totp.getEnabled(),totp!=null&&totp.getPendingExpiresAt()!=null&&totp.getPendingExpiresAt().isAfter(Instant.now()),
                totp==null?null:totp.getEnabledAt(),password!=null,password==null?null:password.getChangedAt(),secrets.configured());
    }
}
