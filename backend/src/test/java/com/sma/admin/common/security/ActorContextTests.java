package com.sma.admin.common.security;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ActorContextTests {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void anonymousAndUnknownPrincipalsCannotUseDefaultTenantAsAuthorization() {
        assertThat(CurrentUser.tenantId()).isEqualTo(1); // Existing bootstrap compatibility only.
        assertThatThrownBy(ActorContext::require).isInstanceOf(AccessDeniedException.class);
        TenantScope.within(23, () -> {
            assertThatThrownBy(ActorContext::tenantId).isInstanceOf(AccessDeniedException.class);
            return null;
        });
        set("admin");
        assertThatThrownBy(ActorContext::require).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("test", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThatThrownBy(ActorContext::require).isInstanceOf(AccessDeniedException.class);
    }

    @Test void applicationUsersCarryTheirOwnTenantAndCannotBecomeIamUsers() {
        set(new AppUserPrincipal(41, 23, 31, 32, "shop", 99));
        assertThat(ActorContext.require()).isEqualTo(new ActorRef(ActorRef.Kind.APP_USER, 23, "41", 31L, 32L));
        TenantScope.within(1, () -> { assertThat(CurrentUser.tenantId()).isEqualTo(23); return null; });
        assertThat(CurrentUser.userId()).isZero();
        assertThatThrownBy(CurrentUser::require).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(CurrentUser::optional).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ActorContext.requireTenant(24)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ActorContext.requireApplicationUser("other")).isInstanceOf(AccessDeniedException.class);
        assertThat(ActorContext.requireApplicationUser("shop").clientId()).isEqualTo(32);
    }

    @Test void machineAndAdminRemainDistinct() {
        set(SmaPrincipal.forApplication(23, "admin", "service"));
        assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.APPLICATION);
        assertThatThrownBy(CurrentUser::require).isInstanceOf(AccessDeniedException.class);
        set(new SmaPrincipal(41, "admin", 23, "admin", null, true, DataAccess.none(), Set.of(), "UTC"));
        assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.ADMIN_USER);
        assertThatThrownBy(() -> ActorContext.withinJob(24, "file:123", () -> null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(ActorContext.tenantId()).isEqualTo(23);
    }

    @Test void nestedJobScopesRestoreAuthenticationAndTenantEvenOnFailure() {
        set(new AppUserPrincipal(41, 23, 31, 32, "shop", 99));
        var previous = SecurityContextHolder.getContext();
        TenantScope.within(17, () -> {
            assertThatThrownBy(() -> ActorContext.withinJob(23, "mail:123", () -> {
                assertThat(CurrentUser.tenantId()).isEqualTo(23);
                assertThat(CurrentUser.userId()).isZero();
                assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);
                ActorContext.withinJob(23, "mail:456", () -> null);
                assertThat(ActorContext.require().subject()).isEqualTo("mail:123");
                throw new IllegalStateException("simulated worker failure");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(TenantScope.current()).isEqualTo(17);
            assertThat(SecurityContextHolder.getContext()).isSameAs(previous);
            return null;
        });
    }

    @Test void pooledWorkerDoesNotInheritOrLeakThePreviousTenant() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThat(executor.submit(() -> ActorContext.withinJob(23, "file:1", ActorContext::tenantId)).get()).isEqualTo(23);
            assertThat(executor.submit(() -> {
                assertThatThrownBy(ActorContext::require).isInstanceOf(AccessDeniedException.class);
                return ActorContext.withinJob(24, "file:2", ActorContext::tenantId);
            }).get()).isEqualTo(24);
        }
    }

    private void set(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
