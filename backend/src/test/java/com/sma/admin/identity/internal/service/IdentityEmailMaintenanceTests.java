package com.sma.admin.identity.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import com.sma.admin.support.DeploymentWorkFixture;
import com.sma.admin.common.security.*;
import org.junit.jupiter.api.Test;

class IdentityEmailMaintenanceTests {
    @Test void drainAfterEmailCleanupDoesNotStartReauthenticationOrSkipTenant(){
        var admission=new DeploymentWorkFixture();var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityEmailChallengeStore.class);var reauthentication=mock(IdentityReauthenticationStore.class);
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(1131,false),new TenantWorkQuery.Entry(1132,true)));
        when(store.maintain()).thenAnswer(i->{assertThat(admission.active).hasValue(1);admission.accepting=false;return 1;});
        var sweep=new IdentityEmailMaintenance(tenants,store,reauthentication,new IdentityEmailMaintenance.Settings(true,2,10000L),admission);
        admission.accepting=false;sweep.poll();verifyNoInteractions(tenants,store,reauthentication);
        admission.accepting=true;sweep.poll();verify(store).maintain();verifyNoInteractions(reauthentication);assertThat(admission.active).hasValue(0);
        sweep.poll();verify(tenants,times(1)).page(anyLong(),anyInt());
        admission.accepting=true;doReturn(1).when(store).maintain();
        when(reauthentication.maintain()).thenAnswer(i->{assertThat(admission.active).hasValue(1);return 1;});sweep.poll();
        verify(tenants,times(2)).page(0,2);verify(store,times(3)).maintain();verify(reauthentication,times(2)).maintain();assertThat(admission.active).hasValue(0);
    }
    @Test void failingBothKindsOfCleanupDoesNotLeakPermits(){
        var admission=new DeploymentWorkFixture();var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityEmailChallengeStore.class);var reauthentication=mock(IdentityReauthenticationStore.class);
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(1131,false),new TenantWorkQuery.Entry(1132,true)));
        when(store.maintain()).thenThrow(new IllegalStateException("unavailable"));when(reauthentication.maintain()).thenThrow(new IllegalStateException("unavailable"));
        new IdentityEmailMaintenance(tenants,store,reauthentication,new IdentityEmailMaintenance.Settings(true,2,10000L),admission).poll();
        verify(store,times(2)).maintain();verify(reauthentication,times(2)).maintain();assertThat(admission.active).hasValue(0);
    }
    @Test void httpJobAndOuterTransactionCannotEnterHostMaintenance(){
        var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityEmailChallengeStore.class);var reauthentication=mock(IdentityReauthenticationStore.class);
        var sweep=new IdentityEmailMaintenance(tenants,store,reauthentication,new IdentityEmailMaintenance.Settings(true,2,10000L),new DeploymentWorkFixture());
        com.sma.admin.support.MaintenanceCallerAssertions.hostOnly(sweep::poll);verifyNoInteractions(tenants,store,reauthentication);
    }
    @Test void boundedSweepUsesTrustedTenantAndContinuesAfterOneTenantFailure(){
        var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityEmailChallengeStore.class);var reauthentication=mock(IdentityReauthenticationStore.class);
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(1131,false),new TenantWorkQuery.Entry(1132,true)));
        when(tenants.page(1132,2)).thenReturn(List.of());
        when(store.maintain()).thenAnswer(i->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);
            if(ActorContext.tenantId()==1131)throw new IllegalStateException("test failure");return 1;});
        when(reauthentication.maintain()).thenAnswer(i->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);return 1;});
        var sweep=new IdentityEmailMaintenance(tenants,store,reauthentication,new IdentityEmailMaintenance.Settings(true,2,10000L),new DeploymentWorkFixture());sweep.poll();sweep.poll();sweep.poll();
        verify(store,times(4)).maintain();verify(reauthentication,times(4)).maintain();verify(tenants,times(2)).page(0,2);assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void disabledMaintenanceAndInvalidBudgetsCannotStartWork(){
        var tenants=mock(TenantWorkQuery.class);var store=mock(IdentityEmailChallengeStore.class);var reauthentication=mock(IdentityReauthenticationStore.class);
        new IdentityEmailMaintenance(tenants,store,reauthentication,new IdentityEmailMaintenance.Settings(false,10,10000L),new DeploymentWorkFixture()).poll();verifyNoInteractions(tenants,store,reauthentication);
        assertThatThrownBy(()->new IdentityEmailMaintenance.Settings(true,51,10000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new IdentityEmailProperties(null,null,null,0,null,null,null,null,null,null,null,null,null,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->IdentityEmailBudget.address("localhost")).isInstanceOf(com.sma.admin.common.BizException.class);
        assertThat(IdentityEmailBudget.address("127.000.0.1")).isEqualTo("127.0.0.1");
    }
}
