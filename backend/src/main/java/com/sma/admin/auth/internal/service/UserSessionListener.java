package com.sma.admin.auth.internal.service;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.iam.UserChangedEvent;

@Component
public class UserSessionListener {
    private final SessionMapper mapper;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final SecurityMaintenance security;
    public UserSessionListener(SessionMapper mapper,org.springframework.context.ApplicationEventPublisher events,SecurityMaintenance security) { this.mapper = mapper;this.events=events;this.security=security; }
    @org.springframework.scheduling.annotation.Async("deploymentEventExecutor") @ApplicationModuleListener
    public void on(UserChangedEvent event) {
        if (event.action().equals("create")) return;
        TenantScope.within(event.tenantId(), () -> {
            if(event.action().equals("delete"))security.removeAccount(event.userId());
            var query = new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getUserId, event.userId());
            if (!event.action().equals("delete")) query.lt(SessionEntity::getCredentialVersion, event.credentialVersion());
            var previous=mapper.selectList(query);mapper.delete(query);
            previous.forEach(session->events.publishEvent(new com.sma.admin.auth.SessionEndedEvent(event.tenantId(),event.userId(),session.getId(),"ACCOUNT_CHANGED")));
            return null;
        });
    }
}
