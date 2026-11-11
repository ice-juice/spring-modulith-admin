package com.sma.admin.auth.internal.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.sma.admin.auth.AuthErrorCode;
import com.sma.admin.auth.internal.persistence.TotpCredentialMapper;
import com.sma.admin.common.BizException;
import com.sma.admin.iam.*;

@Service
public class SecurityReauthentication {
    private final SecurityRateLimit limits;private final AccountQuery accounts;private final AccountSecurityStore security;
    private final TotpCodes codes;private final SecuritySecrets secrets;private final TotpCredentialMapper totps;private final SecurityChallenges clock;
    public SecurityReauthentication(SecurityRateLimit limits,AccountQuery accounts,AccountSecurityStore security,TotpCodes codes,SecuritySecrets secrets,TotpCredentialMapper totps,SecurityChallenges clock){
        this.limits=limits;this.accounts=accounts;this.security=security;this.codes=codes;this.secrets=secrets;this.totps=totps;this.clock=clock;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void verify(AccountView user,String password,String code,String ip,boolean requireTotp){
        limits.claim("REAUTH_IP",ip,60,Duration.ofMinutes(15));
        limits.claim("REAUTH_ACCOUNT",user.portal()+":"+user.id(),10,Duration.ofMinutes(15));
        if(password==null||password.length()>128||!accounts.matchesPassword(user.id(),password))throw new BizException(AuthErrorCode.INVALID_CREDENTIALS);
        var credential=security.lockedTotp(user.id(),user.portal());
        boolean enabled=credential!=null&&credential.getEnabled();
        if(requireTotp&&!enabled)throw new BizException(AuthErrorCode.SECURITY_STATE_CHANGED);
        if(enabled){
            limits.claim("TOTP_ACCOUNT",user.portal()+":"+user.id(),5,Duration.ofMinutes(15));
            long step=codes.acceptedStep(secrets.decrypt(credential.getSecretCiphertext(),SessionIssuer.context(user)),code,clock.now(),credential.getLastAcceptedStep());
            if(step<0)throw new BizException(AuthErrorCode.INVALID_TOTP);
            credential.setLastAcceptedStep(step);totps.updateById(credential);
        }
    }
}
