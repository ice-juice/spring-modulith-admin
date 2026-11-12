package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.request.BusinessOutboxConsumer;
import com.sma.admin.support.*;

class StorageOutboxWorkerTests {
    private final StoragePoolRegistry pools=mock(StoragePoolRegistry.class);
    private final DeploymentWorkFixture admission=new DeploymentWorkFixture();
    private final List<BusinessOutboxConsumer> consumers=List.of(mock(BusinessOutboxConsumer.class));

    @Test void queuedCallbackRechecksAndDoesNotReserveOrInheritIdentity()throws Exception{
        var executor=mock(ExecutorService.class);var queue=new LinkedBlockingQueue<Runnable>();
        doAnswer(i->{queue.add(i.getArgument(0));return null;}).when(executor).execute(any());
        when(pools.activeResourceKeys()).thenReturn(List.of("first:1"));when(pools.deliverNext("first",1,consumers)).thenReturn(Optional.of(false));
        try(var worker=new StorageOutboxWorker(pools,consumers,admission,executor)){
            admission.accepting=false;worker.tick();worker.pollOnce();verifyNoInteractions(pools);assertThat(queue).isEmpty();
            admission.accepting=true;worker.tick();worker.tick();assertThat(queue).hasSize(1);assertThat(admission.active).hasValue(0);
            admission.accepting=false;queue.remove().run();verifyNoInteractions(pools);
            admission.accepting=true;worker.tick();queue.remove().run();verify(pools).deliverNext("first",1,consumers);assertThat(admission.active).hasValue(0);
        }
    }
    @Test void refusedResourceIsRetriedOnRecoveryAndExceptionsDoNotRetryWithinTheBatch(){
        when(pools.activeResourceKeys()).thenReturn(List.of("first:1","second:2"));
        when(pools.deliverNext("first",1,consumers)).thenReturn(Optional.empty(),Optional.of(true));
        when(pools.deliverNext("second",2,consumers)).thenThrow(new IllegalStateException("unknown"));
        try(var worker=new StorageOutboxWorker(pools,consumers,admission)){
            worker.pollOnce();verify(pools,never()).deliverNext(eq("second"),anyLong(),anyList());
            worker.pollOnce();verify(pools,times(2)).deliverNext("first",1,consumers);verify(pools,times(1)).deliverNext("second",2,consumers);
            assertThat(admission.active).hasValue(0);
        }
    }
    @Test void atMostEightResourcesPerTickWithFairCursorAndChangingInventory(){
        var keys=java.util.stream.IntStream.range(0,10).mapToObj(i->"pool"+i+":1").toList();
        when(pools.activeResourceKeys()).thenReturn(keys);
        when(pools.deliverNext(anyString(),anyLong(),anyList())).thenReturn(Optional.of(false));
        try(var worker=new StorageOutboxWorker(pools,consumers,admission)){
            worker.pollOnce();verify(pools,times(8)).deliverNext(anyString(),anyLong(),anyList());
            worker.pollOnce();verify(pools).deliverNext("pool8",1,consumers);verify(pools).deliverNext("pool9",1,consumers);
            when(pools.activeResourceKeys()).thenReturn(List.of("small:1"));worker.pollOnce();verify(pools).deliverNext("small",1,consumers);
        }
    }
    @Test void strictHostNoConsumerAndCloseNeverStartBusinessWork(){
        try(var worker=new StorageOutboxWorker(pools,consumers,admission)){
            MaintenanceCallerAssertions.hostOnly(worker::tick,worker::pollOnce);
            worker.close();worker.tick();worker.pollOnce();verifyNoInteractions(pools);
        }
        try(var worker=new StorageOutboxWorker(pools,List.of(),admission)){worker.tick();worker.pollOnce();verifyNoInteractions(pools);}
    }
    @Test void actualThreadDoesNotInheritRequestLocals()throws Exception{
        var inherited=new InheritableThreadLocal<String>();inherited.set("caller");var entered=new CountDownLatch(1);
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        when(pools.activeResourceKeys()).thenAnswer(i->{
            try{assertThat(inherited.get()).isNull();assertThat(admission.active).hasValue(1);}
            catch(Throwable error){failure.set(error);}finally{entered.countDown();}return List.of();
        });
        try(var worker=new StorageOutboxWorker(pools,consumers,admission)){
            worker.tick();assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();assertThat(failure.get()).isNull();
        }finally{inherited.remove();}
    }
}
