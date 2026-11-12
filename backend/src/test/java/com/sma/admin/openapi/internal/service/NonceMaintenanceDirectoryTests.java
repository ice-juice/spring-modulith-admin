package com.sma.admin.openapi.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.sma.admin.support.DeploymentWorkFixture;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.sma.admin.openapi.internal.persistence.NonceMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class NonceMaintenanceDirectoryTests {
    @Test void queuedCallbackRechecksQualificationAndDoesNotReserveBeforeExecution(){
        var admission=new DeploymentWorkFixture();var nonces=mock(NonceMapper.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        var workers=mock(java.util.concurrent.ExecutorService.class);var queue=new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        doAnswer(i->{queue.add(i.getArgument(0));return null;}).when(workers).execute(any());
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(51,true)));when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        try(var maintenance=new NonceMaintenance(nonces,tenants,new NonceMaintenance.Settings(true,2,1000L),manager,admission,workers)){
            admission.accepting=false;maintenance.purge();maintenance.pollOnce();assertThat(queue).isEmpty();verifyNoInteractions(tenants,manager);
            admission.accepting=true;maintenance.purge();assertThat(queue).hasSize(1);assertThat(admission.active).hasValue(0);
            admission.accepting=false;queue.remove().run();verifyNoInteractions(tenants,manager);
            admission.accepting=true;maintenance.purge();queue.remove().run();verify(nonces).purge();assertThat(admission.active).hasValue(0);
        }
    }
    @Test void commitKeepsPermitAndStopsFollowingTenantUntilQualificationRecovers(){
        var admission=new DeploymentWorkFixture();var nonces=mock(NonceMapper.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(i->{assertThat(admission.active).hasValue(1);return new SimpleTransactionStatus();});
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(51,false),new TenantWorkQuery.Entry(52,true)));
        when(tenants.page(51,2)).thenReturn(List.of(new TenantWorkQuery.Entry(52,true)));
        doAnswer(i->{assertThat(admission.active).hasValue(1);admission.accepting=false;return null;}).when(manager).commit(any());
        var cleaned=new ArrayList<Long>();when(nonces.purge()).thenAnswer(i->{cleaned.add(ActorContext.tenantId());return 1;});
        try(var maintenance=new NonceMaintenance(nonces,tenants,new NonceMaintenance.Settings(true,2,1000L),manager,admission)){
            maintenance.pollOnce();assertThat(cleaned).containsExactly(51L);assertThat(admission.active).hasValue(0);
            maintenance.pollOnce();verify(tenants,times(1)).page(anyLong(),anyInt());
            admission.accepting=true;doNothing().when(manager).commit(any());maintenance.pollOnce();
            assertThat(cleaned).containsExactly(51L,52L);verify(tenants).page(51,2);assertThat(admission.active).hasValue(0);
            doThrow(new IllegalStateException("commit unavailable")).when(manager).commit(any());
            assertThatThrownBy(()->maintenance.cleanTenant(51)).isInstanceOf(IllegalStateException.class);assertThat(admission.active).hasValue(0);
        }
    }
    @Test void directAndScheduledEntriesCannotLaunderRequestOrJobIdentity(){
        var nonces=mock(NonceMapper.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        try(var maintenance=new NonceMaintenance(nonces,tenants,new NonceMaintenance.Settings(true,2,1000L),manager,new DeploymentWorkFixture())){
            com.sma.admin.support.MaintenanceCallerAssertions.hostOnly(maintenance::purge,maintenance::pollOnce,()->maintenance.cleanTenant(51));
            verifyNoInteractions(nonces,tenants,manager);maintenance.close();assertThat(maintenance.cleanTenant(51)).isZero();verifyNoInteractions(manager);
        }
    }
    @Test void sweepsUnseenInactiveTenantsAndWrapsWithoutRetainingTrafficHistory(){
        var nonces=mock(NonceMapper.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(51,false),new TenantWorkQuery.Entry(52,true)));
        when(tenants.page(52,2)).thenReturn(List.of());var cleaned=new ArrayList<Long>();
        when(nonces.purge()).thenAnswer(call->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);cleaned.add(CurrentUser.tenantId());return 0;});
        try(var maintenance=new NonceMaintenance(nonces,tenants,new NonceMaintenance.Settings(true,2,1000L),manager,new DeploymentWorkFixture())){
            maintenance.pollOnce();maintenance.pollOnce();maintenance.pollOnce();assertThat(cleaned).containsExactly(51L,52L,51L,52L);
            verify(tenants,times(2)).page(0,2);verify(tenants).page(52,2);
        }
    }
    @Test void discoveryFailureAndAuthenticatedCallerCannotFallBackToAnyTenant(){
        var nonces=mock(NonceMapper.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(tenants.page(0,2)).thenThrow(new IllegalStateException("unavailable"));
        try(var maintenance=new NonceMaintenance(nonces,tenants,new NonceMaintenance.Settings(true,2,1000L),manager,new DeploymentWorkFixture())){
            maintenance.pollOnce();verifyNoInteractions(nonces);
            Users.as(Users.principal(51,51,DataAccess.unrestricted()),()->{assertThatThrownBy(maintenance::purge).isInstanceOf(AccessDeniedException.class);return null;});
        }
    }
}
