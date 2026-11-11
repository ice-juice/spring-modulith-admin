package com.sma.admin.openapi.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.sma.admin.openapi.internal.persistence.NonceMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class NonceMaintenanceDirectoryTests {
    @Test void sweepsUnseenInactiveTenantsAndWrapsWithoutRetainingTrafficHistory(){
        var nonces=mock(NonceMapper.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(tenants.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(51,false),new TenantWorkQuery.Entry(52,true)));
        when(tenants.page(52,2)).thenReturn(List.of());var cleaned=new ArrayList<Long>();
        when(nonces.purge()).thenAnswer(call->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);cleaned.add(CurrentUser.tenantId());return 0;});
        try(var maintenance=new NonceMaintenance(nonces,tenants,new NonceMaintenance.Settings(true,2,1000L),manager)){
            maintenance.pollOnce();maintenance.pollOnce();maintenance.pollOnce();assertThat(cleaned).containsExactly(51L,52L,51L,52L);
            verify(tenants,times(2)).page(0,2);verify(tenants).page(52,2);
        }
    }
    @Test void discoveryFailureAndAuthenticatedCallerCannotFallBackToAnyTenant(){
        var nonces=mock(NonceMapper.class);var tenants=mock(TenantWorkQuery.class);var manager=mock(PlatformTransactionManager.class);
        when(tenants.page(0,2)).thenThrow(new IllegalStateException("unavailable"));
        try(var maintenance=new NonceMaintenance(nonces,tenants,new NonceMaintenance.Settings(true,2,1000L),manager)){
            maintenance.pollOnce();verifyNoInteractions(nonces);
            Users.as(Users.principal(51,51,DataAccess.unrestricted()),()->{assertThatThrownBy(maintenance::purge).isInstanceOf(AccessDeniedException.class);return null;});
        }
    }
}
