package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import static com.sma.admin.support.DeploymentWorkFixture.host;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.service.*;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SocketSignalDrainTests {
    private static final long TENANT=97270,USER=71;
    @Autowired DeploymentWorkCoordinator admission;
    @Autowired SocketSignalStore signals;
    @Autowired PlatformTransactionManager transactions;

    @Test void committedSignalsRemainReadableAfterDrainWhileOnlyTheAdmittedPushCompletes()throws Exception{
        job(()->new TransactionTemplate(transactions).execute(status->{
            signals.publish("admin",Set.of(USER),"notice",101,null);
            signals.publish("admin",Set.of(USER),"todo",102,null);return null;
        }));
        var before=job(()->signals.read("admin",Set.of(USER)));
        assertThat(before).hasSize(2);
        var hub=mock(SocketHub.class);when(hub.subscriptions()).thenReturn(Set.of(new SocketHub.Audience(TENANT,"admin",USER)));
        var directory=mock(TenantWorkQuery.class);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new AtomicInteger();
        doAnswer(call->{
            assertThat(ActorContext.tenantId()).isEqualTo(TENANT);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(admission.snapshot().activeWork()).isOne();entered.countDown();
            if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Signal push not released");
            done.incrementAndGet();return null;
        }).when(hub).push(eq(TENANT),eq("admin"),eq(Set.of(USER)),anyString(),anyLong());
        try(var processor=new SocketSignalProcessor(hub,signals,new SocketSignalProperties(null,null,null,null,null),directory,admission)){
            try{
                host(processor::tick);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(host(admission::beginDrain)).isTrue();assertThat(admission.snapshot().activeWork()).isOne();
            }finally{release.countDown();}
            await().atMost(Duration.ofSeconds(5)).until(()->admission.snapshot().activeWork()==0);
            assertThat(done).hasValue(1);verify(hub,times(1)).push(anyLong(),anyString(),anySet(),anyString(),anyLong());verifyNoInteractions(directory);
            host(processor::tick);verify(hub,times(1)).subscriptions();
            assertThat(job(()->signals.read("admin",Set.of(USER)))).containsExactlyInAnyOrderElementsOf(before);
            assertThat(job(()->signals.read("customer",Set.of(USER)))).isEmpty();
        }
    }
    private <T>T job(Supplier<T> action){return ActorContext.withinJob(TENANT,"message-signal:drain-test",action);}
}
