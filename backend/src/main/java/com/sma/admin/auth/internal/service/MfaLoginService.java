package com.sma.admin.auth.internal.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import com.sma.admin.common.BizException;

@Service
public class MfaLoginService {
    private final SecurityChallenges challenges;private final SecurityRateLimit limits;private final SessionIssuer issuer;private final LoginEvents events;
    public MfaLoginService(SecurityChallenges challenges,SecurityRateLimit limits,SessionIssuer issuer,LoginEvents events){this.challenges=challenges;this.limits=limits;this.issuer=issuer;this.events=events;}
    public SessionService.LoginResult verify(String token,String code,String ip,String agent){
        com.sma.admin.auth.internal.persistence.SecurityChallengeEntity challenge;
        try { limits.claim("MFA_IP",ip,60,Duration.ofMinutes(15));challenge=challenges.reserve(token,"LOGIN_TOTP"); }
        catch(BizException failure){events.record(null,"",com.sma.admin.common.security.CurrentUser.portal(),"MFA_REJECTED",ip,agent);throw failure;}
        try{limits.claim("TOTP_ACCOUNT",challenge.getPortal()+":"+challenge.getUserId(),5,Duration.ofMinutes(15));return issuer.complete(token,challenge,code,ip,agent);}
        catch(BizException failure){events.record(challenge.getUserId(),"",challenge.getPortal(),"MFA_FAILURE",ip,agent);throw failure;}
    }
}
