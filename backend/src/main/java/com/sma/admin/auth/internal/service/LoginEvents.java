package com.sma.admin.auth.internal.service;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.sma.admin.auth.LoginEvent;
import com.sma.admin.common.security.CurrentUser;

@Service
public class LoginEvents {
    private final ApplicationEventPublisher events;
    public LoginEvents(ApplicationEventPublisher events) { this.events=events; }
    @Transactional(propagation=Propagation.REQUIRED)
    public void record(Long userId,String username,String portal,String status,String ip,String userAgent) {
        String agent=userAgent==null?null:userAgent.substring(0,Math.min(userAgent.length(),512));
        events.publishEvent(new LoginEvent(CurrentUser.tenantId(),userId,username,portal,status,ip,agent,Instant.now()));
    }
}
