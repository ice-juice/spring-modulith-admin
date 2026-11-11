package com.sma.admin.auth.internal.service;

import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Shared, fixed-window pre-reservation; password checks never clear the MFA budget. */
@Component
public class LoginThrottle {
    private final SecurityRateLimit limits;
    public LoginThrottle(SecurityRateLimit limits){this.limits=limits;}
    public void claimCaptcha(String ip){limits.claim("CAPTCHA_IP",ip,30,Duration.ofMinutes(1));}
    public String key(String ip,String username){return Tokens.hash(ip+":"+username);}
    public void check(String key){limits.claim("PASSWORD",key,5,Duration.ofMinutes(5));}
    public void claimLogin(String ip,String portal,String username){
        limits.claim("LOGIN_IP",ip,60,Duration.ofMinutes(5));
        limits.claim("LOGIN_ACCOUNT",portal+":"+username.toLowerCase(Locale.ROOT),10,Duration.ofMinutes(5));
    }
    public void fail(String key){ /* Reserved before verification. */ }
    public void success(String key){ /* Never clear a window on partial authentication. */ }
}
