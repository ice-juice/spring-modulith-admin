package com.sma.admin.auth.internal.service;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
@Component
public class SecurityPasswordHash {
    private final SecuritySecrets keys;
    private final Argon2PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
    public SecurityPasswordHash(SecuritySecrets keys){this.keys=keys;}
    public String currentKey(){return keys.pepperId();}
    public String encode(String candidate,String keyId){return encoder.encode(keys.prehash(candidate,keyId));}
    public boolean matches(String candidate,String encoded,String keyId){
        if(encoded==null||!encoded.matches("\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}"))
            throw new com.sma.admin.common.BizException(com.sma.admin.auth.AuthErrorCode.SECURITY_UNAVAILABLE);
        return candidate!=null&&encoder.matches(keys.prehash(candidate,keyId),encoded);
    }
}
