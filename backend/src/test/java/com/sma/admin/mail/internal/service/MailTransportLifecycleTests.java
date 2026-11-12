package com.sma.admin.mail.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.persistence.MailProfileRevisionEntity;

class MailTransportLifecycleTests {
    @Test void bothCompletionOrdersAndRepeatedCloseReleaseExactlyOnce(){
        for(boolean transportFirst:List.of(true,false)){
            var closed=new AtomicInteger();var attempt=new MailDispatchAttempt(closed::incrementAndGet);var transport=attempt.transport();
            if(transportFirst)transport.close();else attempt.close();
            assertThat(closed).hasValue(0);
            if(transportFirst)attempt.close();else transport.close();
            attempt.close();transport.close();assertThat(closed).hasValue(1);
            assertThatThrownBy(attempt::transport).isInstanceOf(IllegalStateException.class);
        }
        var closed=new AtomicInteger();var attempt=new MailDispatchAttempt(closed::incrementAndGet);attempt.close();attempt.close();
        assertThat(closed).hasValue(1);
    }

    @Test void cancelledRunningFutureDoesNotMeanPhysicalCompletion()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var closed=new AtomicInteger();
        var task=new SmtpMailSender.TrackedTask(()->{entered.countDown();waitUninterruptibly(release);return MailWorkService.Outcome.ACCEPTED;},closed::incrementAndGet);
        var thread=Thread.ofPlatform().start(task);
        try{
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();assertThat(task.cancel(true)).isTrue();
            assertThat(task.isDone()).isTrue();assertThat(closed).hasValue(0);
            release.countDown();thread.join(3000);assertThat(thread.isAlive()).isFalse();assertThat(closed).hasValue(1);
            task.cancel(true);task.run();assertThat(closed).hasValue(1);
        }finally{release.countDown();thread.join(3000);}
    }

    @Test void cancelledBeforeExecutionNeverRunsAndReleasesOnlyOnce(){
        var invoked=new AtomicInteger();var closed=new AtomicInteger();
        var task=new SmtpMailSender.TrackedTask(()->{invoked.incrementAndGet();return MailWorkService.Outcome.ACCEPTED;},closed::incrementAndGet);
        assertThat(task.cancel(true)).isTrue();task.run();task.cancel(true);
        assertThat(invoked).hasValue(0);assertThat(closed).hasValue(1);
    }

    @Test void timeoutWhileDnsIgnoresInterruptRetainsPermitAndDoesNotInheritRequestLocals()throws Exception{
        var settings=settings();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var inherited=new InheritableThreadLocal<String>();inherited.set("request-local");
        var network=new SmtpNetworkPolicy(settings){@Override public InetAddress authorize(String host,int port){
            assertThat(inherited.get()).isNull();entered.countDown();waitUninterruptibly(release);return InetAddress.getLoopbackAddress();
        }};
        var secrets=mock(TenantSecretCodec.class);var sender=new SmtpMailSender(settings,network,secrets,new MailWorkProperties(0,0,null));
        var closed=new AtomicInteger();var attempt=new MailDispatchAttempt(closed::incrementAndGet);
        try{
            assertThat(ActorContext.withinJob(951,"test",()->sender.send(prepared(),attempt.transport()))).isEqualTo(MailWorkService.Outcome.RETRYABLE);
            assertThat(entered.getCount()).isZero();attempt.close();assertThat(closed).hasValue(0);
            var second=new AtomicInteger();
            assertThat(ActorContext.withinJob(951,"test",()->sender.send(prepared(),second::incrementAndGet))).isEqualTo(MailWorkService.Outcome.RETRYABLE);
            assertThat(second).hasValue(1);assertThat(closed).hasValue(0); // saturated thread is still alive
            release.countDown();await().atMost(Duration.ofSeconds(3)).untilAtomic(closed,org.hamcrest.Matchers.is(1));
            verifyNoInteractions(secrets); // expired before any connection/credential use
        }finally{release.countDown();attempt.close();sender.close();inherited.remove();}
    }

    @Test void interruptedCallerRetainsRunningTransportUntilItActuallyExits()throws Exception{
        var settings=settings();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var network=new SmtpNetworkPolicy(settings){@Override public InetAddress authorize(String host,int port){
            entered.countDown();waitUninterruptibly(release);return InetAddress.getLoopbackAddress();
        }};
        var sender=new SmtpMailSender(settings,network,mock(TenantSecretCodec.class),new MailWorkProperties(0,0,null));
        var closed=new AtomicInteger();var interrupted=new AtomicBoolean();var result=new AtomicReference<MailWorkService.Outcome>();
        var thread=Thread.ofPlatform().start(()->{
            try(var attempt=new MailDispatchAttempt(closed::incrementAndGet)){
                result.set(ActorContext.withinJob(951,"test",()->sender.send(prepared(),attempt.transport())));
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try{
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();thread.interrupt();thread.join(3000);
            assertThat(thread.isAlive()).isFalse();assertThat(interrupted).isTrue();
            assertThat(result.get()).isEqualTo(MailWorkService.Outcome.RETRYABLE);assertThat(closed).hasValue(0);
            release.countDown();await().atMost(Duration.ofSeconds(3)).untilAtomic(closed,org.hamcrest.Matchers.is(1));
        }finally{release.countDown();sender.close();thread.join(3000);}
    }

    @Test void queuedTimeoutAndShutdownNeverLeakTheTransportBranch(){
        var workers=mock(ExecutorService.class);var queue=new LinkedBlockingQueue<Runnable>();
        doAnswer(i->{queue.add(i.getArgument(0));return null;}).when(workers).execute(any());
        when(workers.shutdownNow()).thenAnswer(i->List.copyOf(queue));
        var network=mock(SmtpNetworkPolicy.class);var sender=new SmtpMailSender(settings(),network,mock(TenantSecretCodec.class),new MailWorkProperties(0,0,null),workers);
        var closed=new AtomicInteger();
        assertThat(ActorContext.withinJob(951,"test",()->sender.send(prepared(),closed::incrementAndGet))).isEqualTo(MailWorkService.Outcome.RETRYABLE);
        assertThat(closed).hasValue(1);sender.close();queue.remove().run();assertThat(closed).hasValue(1);verifyNoInteractions(network);
    }

    @Test void invalidCallerReleasesBranchWithoutScheduling(){
        var workers=mock(ExecutorService.class);var sender=new SmtpMailSender(settings(),mock(SmtpNetworkPolicy.class),mock(TenantSecretCodec.class),new MailWorkProperties(0,0,null),workers);
        var closed=new AtomicInteger();assertThatThrownBy(()->sender.send(prepared(),closed::incrementAndGet))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(closed).hasValue(1);verifyNoInteractions(workers);
    }

    @Test void timeoutAndDataStartHaveOnlyTwoConservativeOutcomes()throws Exception{
        try(var executor=Executors.newFixedThreadPool(2)){
            for(int i=0;i<100;i++){
                var exchange=new SmtpMailSender.Exchange();var barrier=new CyclicBarrier(2);
                var data=executor.submit(()->{barrier.await();return exchange.beginData();});
                var timeout=executor.submit(()->{barrier.await();return exchange.expire();});
                assertThat(data.get(3,TimeUnit.SECONDS)).isEqualTo(timeout.get(3,TimeUnit.SECONDS));
                assertThat(exchange.beginData()).isFalse();
            }
        }
    }

    private static SmtpProperties settings(){return new SmtpProperties(List.of(),null,null,null,Duration.ofMillis(500),1);}
    private static MailWorkService.Prepared prepared(){
        var profile=new MailProfileRevisionEntity();profile.setTenantId(951L);profile.setHost("smtp.example.test");profile.setPort(587);profile.setTlsMode("STARTTLS");
        return new MailWorkService.Prepared(new MailWorkService.Claim(951,99,1,"test"),"id@example.test",new MailPayload("to@example.test","s","b",false),profile);
    }
    private static void waitUninterruptibly(CountDownLatch latch){
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean interrupted=false;
        try{while(true){try{if(latch.await(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS))return;throw new AssertionError("Transport not released");}
            catch(InterruptedException expected){interrupted=true;}}}
        finally{if(interrupted)Thread.currentThread().interrupt();}
    }
}
