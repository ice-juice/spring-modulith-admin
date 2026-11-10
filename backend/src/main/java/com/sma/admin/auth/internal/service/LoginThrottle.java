package com.sma.admin.auth.internal.service;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import com.sma.admin.auth.AuthErrorCode;
import com.sma.admin.common.BizException;

@Component
public class LoginThrottle {
    private record Attempts(int count,Instant until) {}
    private final ConcurrentHashMap<String,Attempts> attempts=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Attempts> challenges=new ConcurrentHashMap<>();
    public void claimCaptcha(String ip) {
        Instant now=Instant.now();
        challenges.entrySet().removeIf(entry -> !entry.getValue().until().isAfter(now));
        if(challenges.size()>50000) throw new BizException(AuthErrorCode.RATE_LIMITED);
        var entry=challenges.compute(ip,(key,old) -> old==null?new Attempts(1,now.plusSeconds(60)):new Attempts(old.count()+1,old.until()));
        if(entry.count()>30) throw new BizException(AuthErrorCode.RATE_LIMITED);
    }
    public String key(String ip,String username) { return Tokens.hash(ip+":"+username); }
    public void check(String key) {
        Instant now=Instant.now();
        attempts.entrySet().removeIf(entry -> entry.getValue().until().isBefore(now));
        var entry=attempts.get(key);
        if(entry!=null&&entry.count()>=5) throw new BizException(AuthErrorCode.RATE_LIMITED);
        if(attempts.size()>50000) throw new BizException(AuthErrorCode.RATE_LIMITED);
    }
    public void fail(String key) { attempts.compute(key,(ignored,old) -> new Attempts(old==null?1:old.count()+1,Instant.now().plusSeconds(300))); }
    public void success(String key) { attempts.remove(key); }
}
