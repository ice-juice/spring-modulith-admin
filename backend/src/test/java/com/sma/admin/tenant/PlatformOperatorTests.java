package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;
import com.sma.admin.tenant.internal.service.PlatformOperators;
import com.sma.admin.tenant.internal.service.TenantPlatformProperties;

class PlatformOperatorTests {
    @Test void tenantSuperAdminOrSelfGrantedPermissionCannotBecomePlatformOperator() {
        var operators=new PlatformOperators(new TenantPlatformProperties(1L,Set.of(10L),Set.of()));
        for (var user : new SmaPrincipal[]{
                new SmaPrincipal(10,"foreign",2,"admin",null,true,DataAccess.unrestricted(),Set.of("tenant:tenant:create"),"UTC"),
                new SmaPrincipal(11,"local",1,"admin",null,true,DataAccess.unrestricted(),Set.of("tenant:tenant:create"),"UTC"),
                new SmaPrincipal(10,"other",1,"other",null,true,DataAccess.unrestricted(),Set.of(),"UTC"),
                SmaPrincipal.forApplication(1,"admin","machine")}) {
            Users.as(user, () -> { assertThatThrownBy(operators::require).isInstanceOf(AccessDeniedException.class); return null; });
        }
        Users.as(Users.principal(10,1,DataAccess.unrestricted()), () -> { assertThatCode(operators::require).doesNotThrowAnyException(); return null; });
    }

    @Test void deploymentConfigurationHasNoImplicitPlatformOperatorOrUnknownHost() {
        var defaults=new TenantPlatformProperties(null,null,null);
        assertThat(defaults.platformAdminUserIds()).isEmpty();
        assertThat(defaults.bootstrapHosts()).isEmpty();
        assertThatThrownBy(() -> new TenantPlatformProperties(1L,Set.of(0L),Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantPlatformProperties(1L,Set.of(),Set.of("Example.com"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantPlatformProperties(1L,Set.of(),Set.of("example.com:443"))).isInstanceOf(IllegalArgumentException.class);
    }
}
