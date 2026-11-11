package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.internal.service.*;

class ConfigurationPlatformAccessTests {
    private final TenantPlatformProperties properties = new TenantPlatformProperties(1L, Set.of(991L), Set.of("localhost"));
    private final TenantConfigurationPlatformAccess access = new TenantConfigurationPlatformAccess(new PlatformOperators(properties), properties);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void actor(long user, long tenant, String portal, boolean admin, String... permissions) {
        var principal = new SmaPrincipal(user, "test", tenant, portal, null, admin, DataAccess.unrestricted(), Set.of(permissions), "UTC");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
    @Test void deploymentIdentityAndTheExactActionPermissionAreBothRequired() {
        actor(991, 1, "admin", false, "config:shared:view");
        assertThat(access.isPlatformTenant()).isTrue(); access.requireOperator("config:shared:view");
        assertThatThrownBy(() -> access.requireOperator("config:shared:publish")).isInstanceOf(AccessDeniedException.class);
        actor(992, 1, "admin", true, "config:shared:publish");
        assertThatThrownBy(() -> access.requireOperator("config:shared:publish")).isInstanceOf(AccessDeniedException.class);
        actor(991, 2, "admin", true, "config:shared:publish");
        assertThat(access.isPlatformTenant()).isFalse(); assertThatThrownBy(() -> access.requireOperator("config:shared:publish")).isInstanceOf(AccessDeniedException.class);
        actor(991, 1, "partner", true, "config:shared:publish");
        assertThatThrownBy(() -> access.requireOperator("config:shared:publish")).isInstanceOf(AccessDeniedException.class);
        actor(991, 1, "admin", false, "config:shared:publish", "iam:user:delete");
        access.requireOperator("config:shared:publish");
        assertThatThrownBy(() -> access.requireOperator("iam:user:delete")).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> access.requireOperator("config:shared:view")).isInstanceOf(AccessDeniedException.class);
    }
}
