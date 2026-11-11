package com.sma.admin.identity.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import com.sma.admin.common.security.*;
import org.junit.jupiter.api.Test;

class IdentityLoginMaintenanceTests {
    @Test void sweepUsesTrustedTenantScopeIncludesInactiveTenantsAndRediscoversOnRestart(){
        var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityLoginStore.class);
        when(tenants.page(0,10)).thenReturn(List.of(new TenantWorkQuery.Entry(1011,true),new TenantWorkQuery.Entry(1012,false)));
        when(store.maintain()).thenAnswer(i->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);
            assertThat(ActorContext.tenantId()).isIn(1011L,1012L);return 1;});
        for(int i=0;i<2;i++)new IdentityLoginMaintenance(tenants,store,new IdentityLoginMaintenance.Settings(true,10,10000L)).poll();
        verify(store,times(4)).maintain();assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void disabledSweepAndInvalidBoundsNeverStartWork(){
        var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityLoginStore.class);
        new IdentityLoginMaintenance(tenants,store,new IdentityLoginMaintenance.Settings(false,10,10000L)).poll();verifyNoInteractions(tenants,store);
        assertThatThrownBy(()->new IdentityLoginMaintenance.Settings(true,51,10000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new IdentityLoginProperties(null,null,0,null,null,null,null,null,null)).isInstanceOf(IllegalArgumentException.class);
    }
}
