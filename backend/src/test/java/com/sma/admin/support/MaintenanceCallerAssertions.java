package com.sma.admin.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.security.DataAccess;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.*;

public final class MaintenanceCallerAssertions {
    private MaintenanceCallerAssertions(){}
    public static void hostOnly(Runnable... entries){
        for(var entry:entries){
            var request=RequestContextHolder.getRequestAttributes();
            try{
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
                assertThatThrownBy(entry::run).isInstanceOf(AccessDeniedException.class);
                RequestContextHolder.resetRequestAttributes();
                ActorContext.withinJob(41,"untrusted-entry",()->{assertThatThrownBy(entry::run).isInstanceOf(AccessDeniedException.class);return null;});
                Users.as(Users.principal(41,41,DataAccess.unrestricted()),()->{assertThatThrownBy(entry::run).isInstanceOf(AccessDeniedException.class);return null;});
                TransactionSynchronizationManager.setActualTransactionActive(true);
                assertThatThrownBy(entry::run).isInstanceOf(IllegalStateException.class);
            }finally{TransactionSynchronizationManager.setActualTransactionActive(false);RequestContextHolder.setRequestAttributes(request);}
        }
    }
}
