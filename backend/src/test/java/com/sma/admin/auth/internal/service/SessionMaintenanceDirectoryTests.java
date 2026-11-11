package com.sma.admin.auth.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.sma.admin.auth.internal.persistence.SessionMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class SessionMaintenanceDirectoryTests {
    @Test void discoversUnseenAndInactiveTenantsWithBoundedPagesAndWrapsCursor(){
        var sessions=mock(SessionMapper.class);var security=mock(SecurityMaintenance.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());when(sessions.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(41,false),new TenantWorkQuery.Entry(42,true)));
        when(tenants.page(42,2)).thenReturn(List.of(new TenantWorkQuery.Entry(43,false)));
        var expired=new ArrayList<Long>();var rotated=new ArrayList<Long>();
        doAnswer(call->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);expired.add(CurrentUser.tenantId());return null;}).when(security).expire();
        when(security.rotateEncryption()).thenAnswer(call->{rotated.add(CurrentUser.tenantId());return 0;});
        try(var maintenance=new SessionMaintenance(sessions,mock(ApplicationEventPublisher.class),manager,security,tenants,new SessionMaintenance.Settings(true,2,1000L))){
            maintenance.pollOnce();maintenance.pollOnce();maintenance.pollOnce();
            assertThat(expired).containsExactly(41L,42L,43L,41L,42L);assertThat(rotated).isEqualTo(expired);
            verify(tenants,times(2)).page(0,2);verify(tenants).page(42,2);
        }
    }
    @Test void directoryFailureDoesNotInventTenantOneAndHttpCannotStartGlobalMaintenance(){
        var sessions=mock(SessionMapper.class);var security=mock(SecurityMaintenance.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(tenants.page(0,2)).thenThrow(new IllegalStateException("unavailable"));
        try(var maintenance=new SessionMaintenance(sessions,mock(ApplicationEventPublisher.class),manager,security,tenants,new SessionMaintenance.Settings(true,2,1000L))){
            maintenance.pollOnce();verifyNoInteractions(sessions,security);
            Users.as(Users.principal(41,41,DataAccess.unrestricted()),()->{assertThatThrownBy(maintenance::clean).isInstanceOf(AccessDeniedException.class);return null;});
        }
    }
}
