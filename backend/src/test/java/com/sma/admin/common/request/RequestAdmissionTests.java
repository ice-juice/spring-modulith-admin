package com.sma.admin.common.request;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.internal.service.RequestExecutionService;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class RequestAdmissionTests {
    private final RequestExecutionMapper rows=mock(RequestExecutionMapper.class);
    private final RequestMutexMapper mutexes=mock(RequestMutexMapper.class);
    private final RequestBudgetMapper budgets=mock(RequestBudgetMapper.class);
    private final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    private final RequestExecutionService admission=new RequestExecutionService(rows,mutexes,budgets,transactions,
            new RequestGovernanceProperties("","v1","",100000L,1));

    @Test void nativeAdmissionAddsNoTransactionOrReceiptAndNestedProtocolsReuseTheSlot() {
        Users.as(Users.principal(7,7,DataAccess.unrestricted()),()->{
            assertThat(admission.execute(work(()->{
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return admission.execute(work(()->"native-result"));
            }))).isEqualTo("native-result");
            assertThatThrownBy(()->admission.execute(work(()->{throw new IllegalStateException("native failure");}))).isInstanceOf(IllegalStateException.class);
            assertThat(admission.execute(work(()->"slot released"))).isEqualTo("slot released");return null;
        });
        verifyNoInteractions(rows,mutexes,budgets,transactions);
    }
    @Test void admissionRejectsConcurrentWorkBeforeItsEffectsAndReleasesAfterCompletion() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var running=pool.submit(()->Users.as(Users.principal(7,7,DataAccess.unrestricted()),()->admission.execute(work(()->{
                entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}return "done";
            }))));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                Users.as(Users.principal(8,7,DataAccess.unrestricted()),()->{
                    assertThatThrownBy(()->admission.execute(work(()->{throw new AssertionError("must not enter");})))
                            .isInstanceOfSatisfying(RequestRejected.class,error->assertThat(error.reason()).isEqualTo(RequestRejected.Reason.UNAVAILABLE));return null;
                });
            } finally {release.countDown();}
            assertThat(running.get(5,TimeUnit.SECONDS)).isEqualTo("done");
        }
        Users.as(Users.principal(8,7,DataAccess.unrestricted()),()->{assertThat(admission.execute(work(()->"next"))).isEqualTo("next");return null;});
        verifyNoInteractions(rows,mutexes,budgets,transactions);
    }
    private <T> RequestExecution.Work<T> work(Supplier<T> action){return new RequestExecution.Work<>(){
        @Override public void authorize(){CurrentUser.require();}
        @Override public T execute(){return action.get();}
    };}
}
