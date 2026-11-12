package com.sma.admin.auth.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import com.sma.admin.support.DeploymentWorkFixture;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.sma.admin.auth.internal.persistence.SessionMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class SessionMaintenanceDirectoryTests {
    @Test void queuedCallbackRechecksQualificationWithoutReservingWork(){
        var admission=new DeploymentWorkFixture();var sessions=mock(SessionMapper.class);var security=mock(SecurityMaintenance.class);
        var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);var workers=mock(java.util.concurrent.ExecutorService.class);
        var queue=new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        doAnswer(i->{queue.add(i.getArgument(0));return null;}).when(workers).execute(any());
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(41,true)));
        when(sessions.now()).thenReturn(Instant.now());when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        try(var maintenance=new SessionMaintenance(sessions,mock(ApplicationEventPublisher.class),manager,security,tenants,new SessionMaintenance.Settings(true,2,1000L),admission,workers)){
            admission.accepting=false;maintenance.clean();maintenance.pollOnce();assertThat(queue).isEmpty();verifyNoInteractions(tenants,manager);
            admission.accepting=true;maintenance.clean();assertThat(queue).hasSize(1);assertThat(admission.active).hasValue(0);
            admission.accepting=false;queue.remove().run();verifyNoInteractions(tenants,manager);
            admission.accepting=true;maintenance.clean();queue.remove().run();verify(security).expire();verify(security).rotateEncryption();
            assertThat(admission.active).hasValue(0);
        }
    }
    @Test void admissionCoversCommitButDrainBetweenExpiryAndRotationStopsWithoutSkippingTenant(){
        var admission=new DeploymentWorkFixture();var sessions=mock(SessionMapper.class);var security=mock(SecurityMaintenance.class);
        var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(i->{assertThat(admission.active).hasValue(1);return new SimpleTransactionStatus();});
        when(sessions.now()).thenReturn(Instant.now());when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(41,true),new TenantWorkQuery.Entry(42,false)));
        doAnswer(i->{assertThat(admission.active).hasValue(1);admission.accepting=false;return null;}).when(manager).commit(any());
        try(var maintenance=new SessionMaintenance(sessions,mock(ApplicationEventPublisher.class),manager,security,tenants,new SessionMaintenance.Settings(true,2,1000L),admission)){
            maintenance.pollOnce();verify(security).expire();verify(security,never()).rotateEncryption();assertThat(admission.active).hasValue(0);
            maintenance.pollOnce();verify(tenants,times(1)).page(anyLong(),anyInt());
            admission.accepting=true;doNothing().when(manager).commit(any());maintenance.pollOnce();
            verify(tenants,times(2)).page(0,2);verify(security,times(3)).expire();verify(security,times(2)).rotateEncryption();assertThat(admission.active).hasValue(0);
            doThrow(new IllegalStateException("commit unavailable")).when(manager).commit(any());
            assertThatThrownBy(()->maintenance.cleanTenant(41)).isInstanceOf(IllegalStateException.class);assertThat(admission.active).hasValue(0);
        }
    }
    @Test void everyEntryRejectsHttpJobAndOuterTransactionBeforeAnyWork(){
        var sessions=mock(SessionMapper.class);var security=mock(SecurityMaintenance.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        try(var maintenance=new SessionMaintenance(sessions,mock(ApplicationEventPublisher.class),manager,security,tenants,new SessionMaintenance.Settings(true,2,1000L),new DeploymentWorkFixture())){
            com.sma.admin.support.MaintenanceCallerAssertions.hostOnly(maintenance::clean,maintenance::pollOnce,()->maintenance.cleanTenant(41));
            verifyNoInteractions(sessions,security,tenants,manager);
            maintenance.close();assertThat(maintenance.cleanTenant(41)).isZero();verifyNoInteractions(manager);
        }
    }
    @Test void discoversUnseenAndInactiveTenantsWithBoundedPagesAndWrapsCursor(){
        var sessions=mock(SessionMapper.class);var security=mock(SecurityMaintenance.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());when(sessions.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(41,false),new TenantWorkQuery.Entry(42,true)));
        when(tenants.page(42,2)).thenReturn(List.of(new TenantWorkQuery.Entry(43,false)));
        var expired=new ArrayList<Long>();var rotated=new ArrayList<Long>();
        doAnswer(call->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);expired.add(CurrentUser.tenantId());return null;}).when(security).expire();
        when(security.rotateEncryption()).thenAnswer(call->{rotated.add(CurrentUser.tenantId());return 0;});
        try(var maintenance=new SessionMaintenance(sessions,mock(ApplicationEventPublisher.class),manager,security,tenants,new SessionMaintenance.Settings(true,2,1000L),new DeploymentWorkFixture())){
            maintenance.pollOnce();maintenance.pollOnce();maintenance.pollOnce();
            assertThat(expired).containsExactly(41L,42L,43L,41L,42L);assertThat(rotated).isEqualTo(expired);
            verify(tenants,times(2)).page(0,2);verify(tenants).page(42,2);
        }
    }
    @Test void directoryFailureDoesNotInventTenantOneAndHttpCannotStartGlobalMaintenance(){
        var sessions=mock(SessionMapper.class);var security=mock(SecurityMaintenance.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(tenants.page(0,2)).thenThrow(new IllegalStateException("unavailable"));
        try(var maintenance=new SessionMaintenance(sessions,mock(ApplicationEventPublisher.class),manager,security,tenants,new SessionMaintenance.Settings(true,2,1000L),new DeploymentWorkFixture())){
            maintenance.pollOnce();verifyNoInteractions(sessions,security);
            Users.as(Users.principal(41,41,DataAccess.unrestricted()),()->{assertThatThrownBy(maintenance::clean).isInstanceOf(AccessDeniedException.class);return null;});
        }
    }
}
