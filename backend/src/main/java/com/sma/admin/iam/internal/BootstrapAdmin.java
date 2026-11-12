package com.sma.admin.iam.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.common.security.PlatformTenantQuery;
import com.sma.admin.common.security.TenantScope;

@Component
class BootstrapAdmin {
    private final UserService users;
    private final PlatformTenantQuery platform;
    private final String password;
    BootstrapAdmin(UserService users,PlatformTenantQuery platform,@Value("${sma.bootstrap.admin-password:}") String password) { this.users=users;this.platform=platform;this.password=password; }
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() { TenantScope.within(platform.platformTenantId(),()->{users.bootstrap(password);return null;}); }
}
