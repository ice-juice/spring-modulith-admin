package com.sma.admin.openapi.internal.service;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import com.sma.admin.common.BizException;
import com.sma.admin.openapi.OpenapiErrorCode;
@Component
public class OpenLoginThrottle {
 private record Attempts(int failures,Instant until){}
 private final ConcurrentHashMap<String,Attempts> attempts=new ConcurrentHashMap<>();
 public void check(String key){var now=Instant.now();if(attempts.size()>=10000){attempts.values().removeIf(value->!value.until().isAfter(now));if(attempts.size()>=10000)throw new BizException(OpenapiErrorCode.RATE_LIMIT);}
  var row=attempts.get(key);if(row!=null&&row.failures()>=10&&row.until().isAfter(now))throw new BizException(OpenapiErrorCode.RATE_LIMIT);}
 public void fail(String key){attempts.compute(key,(id,previous)->previous==null||!previous.until().isAfter(Instant.now())?new Attempts(1,Instant.now().plusSeconds(300)):new Attempts(previous.failures()+1,previous.until()));}
 public void success(String key){attempts.remove(key);}
}
