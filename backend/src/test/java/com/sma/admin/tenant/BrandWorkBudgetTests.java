package com.sma.admin.tenant;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import com.sma.admin.common.BizException;
import com.sma.admin.tenant.internal.service.BrandWorkBudget;
class BrandWorkBudgetTests {
    @Test void fourTenantTransactionsAreBoundedAndRollbackReleasesTheLease() throws Exception {
        var budget=new BrandWorkBudget();var ready=new java.util.concurrent.CountDownLatch(4);var release=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks=java.util.stream.IntStream.range(0,4).mapToObj(i->executor.submit(()->{
                begin();try{budget.enter();budget.enter();ready.countDown();release.await();}
                finally{finish();}return null;
            })).toList();
            try { assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();begin();
                try { assertThatThrownBy(budget::enter).isInstanceOf(BizException.class); } finally { finish(); }
            } finally { release.countDown(); }
            for(var task:tasks)task.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        begin();try { assertThatCode(budget::enter).doesNotThrowAnyException(); } finally { finish(); }
        assertThatThrownBy(budget::enter).isInstanceOf(IllegalStateException.class);
    }
    private static void begin(){TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);}
    private static void finish(){try{TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));}
        finally{TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);}}
}
