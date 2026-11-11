package com.sma.admin.extension.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;
import com.sma.extension.api.InvocationIdentity;

class WorkCallerTests {
    @Test void applicationScopeRequiresMachineIdentityAndNeverInheritsHumanGrants() {
        Users.as(Users.principal(1,1,DataAccess.unrestricted()),() -> {assertThatThrownBy(() -> WorkCaller.application(Set.of("extension:invoke:example"),() -> null)).hasMessage("application-context-required");return null;});
        Users.as(SmaPrincipal.forApplication(52,"admin","client-one"),() -> {
            assertThatThrownBy(WorkCaller::current).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            WorkCaller.application(Set.of("extension:invoke:example"),() -> {var actor=WorkCaller.current();assertThat(actor.tenantId()).isEqualTo(52);assertThat(actor.subject()).isEqualTo("app:client-one");assertThat(actor.kind()).isEqualTo(InvocationIdentity.Kind.APPLICATION);assertThat(actor.permissions()).containsExactly("extension:invoke:example");return null;});
            assertThatThrownBy(WorkCaller::current).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;
        });
    }
    @Test void backgroundScopeIsLexicalTenantBoundAndCannotWrapAUserRequest() {
        SecurityContextHolder.clearContext();
        WorkCaller.job(51,"scheduled",Set.of("extension:invoke:example"),() -> {
            assertThat(CurrentUser.tenantId()).isEqualTo(51);assertThat(WorkCaller.current().kind()).isEqualTo(InvocationIdentity.Kind.JOB);
            assertThat(WorkCaller.current().subject()).isEqualTo("job:scheduled");
            assertThatThrownBy(() -> WorkCaller.job(52,"nested",Set.of(),() -> null)).hasMessage("job-context-must-be-background");return null;
        });
        assertThat(CurrentUser.tenantId()).isEqualTo(1);assertThatThrownBy(WorkCaller::current).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        Users.as(Users.principal(1,1,DataAccess.unrestricted()),() -> {
            assertThatThrownBy(() -> WorkCaller.job(51,"scheduled",Set.of(),() -> null)).hasMessage("job-context-must-be-background");return null;
        });
        SecurityContextHolder.clearContext();
    }
}
