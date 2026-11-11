package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.internal.service.*;

class LegacyBrandBootstrapTests {
    @Test void pagesTrustedDirectoryAndRestoresTheTenantScopeIncludingSuspendedTenants() {
        var directory=mock(TenantWorkQuery.class);var migration=mock(LegacyBrandMigrationService.class);
        when(directory.page(0,100)).thenReturn(List.of(new TenantWorkQuery.Entry(10,true),new TenantWorkQuery.Entry(20,false)));
        when(directory.page(20,100)).thenReturn(List.of());
        List<Long> migrated=new ArrayList<>();
        when(migration.migrateCurrentTenant()).thenAnswer(call->{migrated.add(CurrentUser.tenantId());return new LegacyBrandMigrationService.Result(true,false,"source");});
        TenantScope.within(999,()->{
            new LegacyBrandMigrationBootstrap(directory,migration).migrate();assertThat(CurrentUser.tenantId()).isEqualTo(999);return null;
        });
        assertThat(migrated).containsExactly(10L,20L);verify(directory).page(20,100);
    }
    @Test void failedTenantStopsStartupAndDoesNotContinueWithLaterTenants() {
        var directory=mock(TenantWorkQuery.class);var migration=mock(LegacyBrandMigrationService.class);
        when(directory.page(0,100)).thenReturn(List.of(new TenantWorkQuery.Entry(10,true),new TenantWorkQuery.Entry(20,true)));
        when(migration.migrateCurrentTenant()).thenThrow(new IllegalStateException("migration conflict"));
        assertThatThrownBy(()->new LegacyBrandMigrationBootstrap(directory,migration).migrate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tenant 10").hasRootCauseMessage("migration conflict");
        verify(migration,times(1)).migrateCurrentTenant();verify(directory,times(1)).page(anyLong(),anyInt());
    }
}
