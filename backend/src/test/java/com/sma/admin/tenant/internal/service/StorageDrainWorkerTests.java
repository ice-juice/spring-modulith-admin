package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.sma.admin.support.MaintenanceCallerAssertions;

class StorageDrainWorkerTests {
    @Test void technicalAcknowledgementsContinueDuringRefusingTrafficAndRetainFailureForNextSweep(){
        var state=mock(StorageSwitchState.class);
        when(state.draining(0)).thenReturn(List.of(11L,12L));when(state.draining(12)).thenReturn(List.of());
        doThrow(new IllegalStateException("not yet drained")).when(state).acknowledgeLocal(11);
        try(var context=new AnnotationConfigApplicationContext()){
            context.registerBean(ApplicationAvailabilityBean.class);context.refresh();
            AvailabilityChangeEvent.publish(context,ReadinessState.REFUSING_TRAFFIC);
            var worker=new StorageDrainWorker(state);worker.tick();worker.tick();worker.tick();
            assertThat(context.getBean(ApplicationAvailabilityBean.class).getReadinessState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
            verify(state,times(2)).acknowledgeLocal(11);verify(state,times(2)).acknowledgeLocal(12);
            verify(state,times(2)).draining(0);verify(state).draining(12);
            verifyNoMoreInteractions(state);
        }
    }
    @Test void httpIdentityAndOuterTransactionCannotBecomeHostProof(){
        var state=mock(StorageSwitchState.class);var worker=new StorageDrainWorker(state);
        MaintenanceCallerAssertions.hostOnly(worker::tick);verifyNoInteractions(state);
    }
}
