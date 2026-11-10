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
    private final com.sma.admin.iam.AccountQuery accounts;
    public LoginEvents(ApplicationEventPublisher events,com.sma.admin.iam.AccountQuery accounts) { this.events=events;this.accounts=accounts; }
    @Transactional(propagation=Propagation.REQUIRED)
    public void record(Long userId,String username,String portal,String status,String ip,String userAgent) {
        String agent=userAgent==null?null:userAgent.substring(0,Math.min(userAgent.length(),512));
        Long departmentId=userId==null?null:accounts.findById(userId).map(com.sma.admin.iam.AccountView::departmentId).orElse(null);
        events.publishEvent(new LoginEvent(java.util.UUID.randomUUID().toString(),CurrentUser.tenantId(),userId,departmentId,username,portal,status,ip,agent,Instant.now()));
    }
}
