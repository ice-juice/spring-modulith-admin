package com.sma.admin.identity.internal.service;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import com.sma.admin.common.*;
import com.sma.admin.common.security.PasswordStrengthPolicy;
import com.sma.admin.identity.IdentityErrorCode;
import org.springframework.boot.context.properties.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Host encoder and write policy, with bounded CPU concurrency and no queue. Does not authenticate a subject. */
@Component @EnableConfigurationProperties(IdentityPasswordWork.Settings.class)
public class IdentityPasswordWork {
    @ConfigurationProperties("sma.identity.password")
    public record Settings(Integer maximumConcurrent,Integer perTargetPerMinute){
        public Settings{maximumConcurrent=maximumConcurrent==null?4:maximumConcurrent;perTargetPerMinute=perTargetPerMinute==null?5:perTargetPerMinute;
            if(maximumConcurrent<1 || maximumConcurrent>16 || perTargetPerMinute<1 || perTargetPerMinute>10)throw new IllegalArgumentException("Invalid application password budget");}
    }
    private final PasswordEncoder encoder;private final PasswordStrengthPolicy policy;private final Semaphore slots;private final String dummyHash;
    public IdentityPasswordWork(PasswordEncoder encoder,PasswordStrengthPolicy policy,Settings settings){
        this.encoder=encoder;this.policy=policy;slots=new Semaphore(settings.maximumConcurrent());
        // Warm once at startup: an unknown account still pays the host encoder's normal verification cost.
        dummyHash=encoder.encode(java.util.UUID.randomUUID().toString());if(dummyHash==null || dummyHash.isBlank())throw new IllegalStateException("Password encoder unavailable");
    }
    public String encode(String email,String candidate){
        if(email==null || !email.contains("@"))throw new BizException(IdentityErrorCode.PASSWORD_FORMAT);
        return switch(policy.evaluate(email.substring(0,email.indexOf('@')),candidate)){
            case INVALID_FORMAT->throw new BizException(IdentityErrorCode.PASSWORD_FORMAT);
            case WEAK->throw new BizException(IdentityErrorCode.WEAK_PASSWORD);
            case ACCEPTED->bounded(()->encoder.encode(candidate));
        };
    }
    /** Historical passwords are verified without applying today's write-strength policy. Never use this boolean alone to issue a session. */
    public boolean matches(String candidate,String encoded){
        if(candidate==null || candidate.isEmpty() || candidate.length()>72 || candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)return false;
        return bounded(()->encoder.matches(candidate,encoded==null?dummyHash:encoded));
    }
    private <T>T bounded(Supplier<T> work){
        if(!slots.tryAcquire())throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE);
        try{return work.get();}finally{slots.release();}
    }
}
