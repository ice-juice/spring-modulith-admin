package com.sma.admin.auth.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class TenantAuthenticationGuardTests {
    @Test void suspendedAndReenabledTenantsNeverRevalidateOldSessionsOrChallenges() {
        var query=mock(TenantEntryQuery.class);var guard=new TenantAuthenticationGuard(query);
        Users.as(Users.principal(7,72,DataAccess.unrestricted()),() -> {
            when(query.securityState(72)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(72,1,true,true)));
            assertThat(guard.capture(72,"admin")).isEqualTo(1);
            assertThat(guard.active(72,"admin",1L)).isTrue();
            assertThat(guard.active(72,"admin",null)).isFalse();
            when(query.securityState(72)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(72,2,false,false)));
            assertThat(guard.active(72,"admin",1L)).isFalse();
            assertThatThrownBy(() -> guard.capture(72,"admin")).isInstanceOf(BizException.class);
            when(query.securityState(72)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(72,3,true,true)));
            assertThat(guard.active(72,"admin",1L)).isFalse();assertThat(guard.active(72,"admin",3L)).isTrue();
            assertThat(guard.active(72,"other",3L)).isFalse();assertThat(guard.active(73,"admin",3L)).isFalse();
            when(query.securityState(72)).thenReturn(Optional.empty());
            assertThat(guard.active(72,"admin",3L)).isFalse();
            return null;
        });
    }

    @Test void requestEntrySnapshotCannotUpgradeItselfAfterDirectoryChanges() {
        var query=mock(TenantEntryQuery.class);var guard=new TenantAuthenticationGuard(query);
        when(query.securityState(72)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(72,3,true,true)));
        try(var request=TenantEntryScope.beginRequest(); var entry=TenantEntryScope.open(new TenantEntry(72,100,"a.example.com","admin",1))) {
            assertThatThrownBy(() -> guard.capture(72,"admin")).isInstanceOf(BizException.class);
            assertThat(guard.active(72,"admin",1L)).isFalse();assertThat(guard.active(72,"admin",3L)).isFalse();
        }
    }

    @Test void trustedBackgroundSessionChecksUsePersistedPortalInsteadOfAnImplicitAdminPortal() {
        var query=mock(TenantEntryQuery.class);var guard=new TenantAuthenticationGuard(query);
        when(query.securityState(72)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(72,1,true,true)));
        assertThat(TenantScope.within(72,() -> guard.active(72,"partner",1L))).isTrue();
        try(var request=TenantEntryScope.beginRequest();var entry=TenantEntryScope.open(new TenantEntry(72,100,"a.example.com","admin",1))) {
            assertThat(guard.active(72,"partner",1L)).isFalse();
        }
    }
}
