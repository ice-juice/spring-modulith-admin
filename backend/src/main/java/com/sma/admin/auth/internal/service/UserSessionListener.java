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
    public UserSessionListener(SessionMapper mapper) { this.mapper = mapper; }
    @ApplicationModuleListener
    public void on(UserChangedEvent event) {
        if (event.action().equals("create")) return;
        TenantScope.within(event.tenantId(), () -> {
            var query = new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getUserId, event.userId());
            if (!event.action().equals("delete")) query.lt(SessionEntity::getCredentialVersion, event.credentialVersion());
            mapper.delete(query);
            return null;
        });
    }
}
