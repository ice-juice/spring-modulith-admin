package com.sma.admin.identity.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import com.sma.admin.support.DeploymentWorkFixture;
import com.sma.admin.common.security.*;
import org.junit.jupiter.api.Test;

class IdentityLoginMaintenanceTests {
    @Test void qualificationStopsBetweenTenantsAndRecoveryResumesAtUnvisitedTenant(){
        var admission=new DeploymentWorkFixture();var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityLoginStore.class);
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(1011,true),new TenantWorkQuery.Entry(1012,false)));
        when(tenants.page(1011,2)).thenReturn(List.of(new TenantWorkQuery.Entry(1012,false)));
        var visited=new java.util.ArrayList<Long>();when(store.maintain()).thenAnswer(i->{
            assertThat(admission.active).hasValue(1);visited.add(ActorContext.tenantId());admission.accepting=false;return 1;
        });
        var sweep=new IdentityLoginMaintenance(tenants,store,new IdentityLoginMaintenance.Settings(true,2,10000L),admission);
        admission.accepting=false;sweep.poll();verifyNoInteractions(tenants,store);
        admission.accepting=true;sweep.poll();assertThat(visited).containsExactly(1011L);assertThat(admission.active).hasValue(0);
        sweep.poll();verify(tenants,times(1)).page(anyLong(),anyInt());
        admission.accepting=true;sweep.poll();assertThat(visited).containsExactly(1011L,1012L);verify(tenants).page(1011,2);
        assertThat(admission.active).hasValue(0);
    }
    @Test void failedMaintenanceReleasesPermitAndDoesNotPreventOtherTenants(){
        var admission=new DeploymentWorkFixture();var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityLoginStore.class);
        when(tenants.page(0,10)).thenReturn(List.of(new TenantWorkQuery.Entry(1011,true),new TenantWorkQuery.Entry(1012,false)));
        when(store.maintain()).thenAnswer(i->{assertThat(admission.active).hasValue(1);throw new IllegalStateException("unavailable");});
        new IdentityLoginMaintenance(tenants,store,new IdentityLoginMaintenance.Settings(true,10,10000L),admission).poll();
        verify(store,times(2)).maintain();assertThat(admission.active).hasValue(0);
    }
    @Test void httpJobAndOuterTransactionCannotEnterHostMaintenance(){
        var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityLoginStore.class);
        var sweep=new IdentityLoginMaintenance(tenants,store,new IdentityLoginMaintenance.Settings(true,10,10000L),new DeploymentWorkFixture());
        com.sma.admin.support.MaintenanceCallerAssertions.hostOnly(sweep::poll);verifyNoInteractions(tenants,store);
    }
    @Test void sweepUsesTrustedTenantScopeIncludesInactiveTenantsAndRediscoversOnRestart(){
        var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityLoginStore.class);
        when(tenants.page(0,10)).thenReturn(List.of(new TenantWorkQuery.Entry(1011,true),new TenantWorkQuery.Entry(1012,false)));
        when(store.maintain()).thenAnswer(i->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);
            assertThat(ActorContext.tenantId()).isIn(1011L,1012L);return 1;});
        for(int i=0;i<2;i++)new IdentityLoginMaintenance(tenants,store,new IdentityLoginMaintenance.Settings(true,10,10000L),new DeploymentWorkFixture()).poll();
        verify(store,times(4)).maintain();assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void disabledSweepAndInvalidBoundsNeverStartWork(){
        var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityLoginStore.class);
        new IdentityLoginMaintenance(tenants,store,new IdentityLoginMaintenance.Settings(false,10,10000L),new DeploymentWorkFixture()).poll();verifyNoInteractions(tenants,store);
        assertThatThrownBy(()->new IdentityLoginMaintenance.Settings(true,51,10000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new IdentityLoginProperties(null,null,0,null,null,null,null,null,null)).isInstanceOf(IllegalArgumentException.class);
    }
}
