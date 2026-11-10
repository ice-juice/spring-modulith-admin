package com.sma.admin.iam.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.sma.admin.iam.internal.service.UserService;

@Component
class BootstrapAdmin {
    private final UserService users;
    private final String password;
    BootstrapAdmin(UserService users,@Value("${sma.bootstrap.admin-password:}") String password) { this.users=users; this.password=password; }
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() { users.bootstrap(password); }
}
