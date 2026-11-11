package com.sma.admin.common.request;

import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.service.RequestExecutionService;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;

/** A deterministic capacity-lock holder represents another application node's short reservation. */
@IntegrationTest
class RequestCapacityContentionTests {
    @Autowired RequestExecutionMapper executions;
    @Autowired RequestMutexMapper mutexes;
    @Autowired RequestBudgetMapper budgets;
    @Autowired PlatformTransactionManager transactions;

    @Test void independentRequestWaitsForBriefCapacityReservationAndCommitsOnce() throws Exception { contend(79931,true); }
    @Test void stalledCapacityReservationRemainsBoundedAndRunsNoBusinessWork() throws Exception { contend(79932,false); }

    private void contend(long tenant,boolean releaseSoon) throws Exception {
        var actor=Users.principal(tenant,tenant,DataAccess.unrestricted());
        String capacity=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("ledger-capacity".getBytes(StandardCharsets.UTF_8)));
        Users.as(actor,()->{mutexes.ensure(IdWorker.getId(),tenant,capacity);return null;});
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempted=new CountDownLatch(1);
        var calls=new AtomicInteger();
        RequestMutexMapper observed=(RequestMutexMapper)Proxy.newProxyInstance(RequestMutexMapper.class.getClassLoader(),new Class<?>[]{RequestMutexMapper.class},(proxy,method,args)->{
            if(method.getName().equals("selectOne"))attempted.countDown();
            try{return method.invoke(mutexes,args);}catch(InvocationTargetException failure){throw failure.getCause();}
        });
        var contender=new RequestExecutionService(executions,observed,budgets,transactions,"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",100000);
        var command=new RequestCommand("config.capacity-contention","config.capacity-contention","independent-object","capacity-contention-key-0001",new byte[]{1},"");
        var work=new RequestExecution.Work<String>() {
            public void authorize(){CurrentUser.require();}
            public String execute(){calls.incrementAndGet();return "completed";}
        };
        var holderTx=new TransactionTemplate(transactions);holderTx.setTimeout(15);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var holder=workers.submit(()->Users.as(actor,()->holderTx.execute(status->{
                mutexes.selectOne(new LambdaQueryWrapper<RequestMutexEntity>().eq(RequestMutexEntity::getResourceHash,capacity).last("FOR UPDATE"));
                held.countDown();
                try{if(!release.await(12,TimeUnit.SECONDS))throw new IllegalStateException("Test holder deadline");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                return null;
            })));
            try {
                assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
                long began=System.nanoTime();
                var result=workers.submit(()->Users.as(actor,()->contender.executeLocal(command,work)));
                assertThat(attempted.await(5,TimeUnit.SECONDS)).as("real capacity query reached").isTrue();
                assertThatThrownBy(()->result.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                if(releaseSoon){
                    release.countDown();
                    assertThat(result.get(5,TimeUnit.SECONDS).receipt().state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
                    Users.as(actor,()->{assertThat(contender.executeLocal(command,work).replayed()).isTrue();return null;});
                    assertThat(calls.get()).isEqualTo(1);
                }else{
                    assertThatThrownBy(()->result.get(7,TimeUnit.SECONDS)).isInstanceOfSatisfying(ExecutionException.class,failure->{
                        assertThat(failure.getCause()).isInstanceOfSatisfying(RequestRejected.class,rejected->assertThat(rejected.reason()).isEqualTo(RequestRejected.Reason.UNAVAILABLE));
                    });
                    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)).isBetween(1000L,7000L);
                    assertThat(calls.get()).isZero();
                    Users.as(actor,()->{assertThat(executions.selectCount(null)).isZero();return null;});
                }
            }finally{release.countDown();holder.get(5,TimeUnit.SECONDS);}
        }
    }
}
