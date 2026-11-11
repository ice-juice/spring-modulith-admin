package com.sma.admin.common.security;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Executes actual method-security proxies, not annotation string comparisons. */
@SpringJUnitConfig(ActorMethodSecurityTests.Config.class)
class ActorMethodSecurityTests {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean static AnnotationTemplateExpressionDefaults templates() { return new AnnotationTemplateExpressionDefaults(); }
        @Bean ProtectedOperations protectedOperations() { return new ProtectedOperations(); }
    }

    static class ProtectedOperations {
        @PermitLoggedIn public String adminProfile() { return "admin"; }
        @RequiresPermission("file:file:upload") public String adminUpload() { return "uploaded"; }
        @PermitApplicationUser("shop") public String appProfile() { return "shop"; }
        @PermitMachine public String machineProfile() { return "machine"; }
        @PermitApplicationSelf public String selfService() { return "self"; }
    }

    @Autowired ProtectedOperations operations;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void appSessionEvenWithForgedAdminAuthorityCannotAccessOldEndpoints() {
        set(new AppUserPrincipal(41, 23, 31, 32, "shop", 99), "file:file:upload");
        assertThat(operations.appProfile()).isEqualTo("shop");
        assertThatThrownBy(operations::adminProfile).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(operations::adminUpload).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(operations::machineProfile).isInstanceOf(AccessDeniedException.class);
    }

    @Test void wrongAudienceAndMachineAreRejected() {
        set(new AppUserPrincipal(41, 23, 31, 32, "other", 99), "file:file:upload");
        assertThatThrownBy(operations::appProfile).isInstanceOf(AccessDeniedException.class);
        set(SmaPrincipal.forApplication(23, "admin", "machine"), "file:file:upload");
        assertThat(operations.machineProfile()).isEqualTo("machine");
        assertThatThrownBy(operations::adminProfile).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(operations::adminUpload).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(operations::appProfile).isInstanceOf(AccessDeniedException.class);
    }

    @Test void adminRequiresPermissionAndCannotEnterConsumerAudience() {
        var admin = new SmaPrincipal(41, "admin", 23, "admin", null, false, DataAccess.none(), Set.of(), "UTC");
        set(admin);
        assertThat(operations.adminProfile()).isEqualTo("admin");
        assertThatThrownBy(operations::adminUpload).isInstanceOf(AccessDeniedException.class);
        set(admin, "file:file:upload");
        assertThat(operations.adminUpload()).isEqualTo("uploaded");
        assertThatThrownBy(operations::appProfile).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(operations::machineProfile).isInstanceOf(AccessDeniedException.class);
    }

    @Test void jobScopeHasNoHumanMethodPermissions() {
        ActorContext.withinJob(23, "mail:1", () -> {
            assertThatThrownBy(operations::adminProfile).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(operations::appProfile).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(operations::machineProfile).isInstanceOf(AccessDeniedException.class);
            return null;
        });
    }
    @Test void platformSelfServiceAcceptsOnlyAuthenticatedApplicationPrincipals(){
        set(new AppUserPrincipal(41,23,31,32,"shop",99));assertThat(operations.selfService()).isEqualTo("self");
        set(new SmaPrincipal(41,"admin",23,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC"));
        assertThatThrownBy(operations::selfService).isInstanceOf(AccessDeniedException.class);
        set(SmaPrincipal.forApplication(23,"admin","machine"));assertThatThrownBy(operations::selfService).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.clearContext();assertThatThrownBy(operations::selfService).hasRootCauseInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(new AppUserPrincipal(41,23,31,32,"shop",99),null));
        assertThatThrownBy(operations::selfService).isInstanceOf(AccessDeniedException.class);
    }

    private void set(Object principal, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,
                null, java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
