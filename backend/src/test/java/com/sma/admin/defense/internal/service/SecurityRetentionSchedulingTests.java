package com.sma.admin.defense.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.common.security.TenantWorkQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class SecurityRetentionSchedulingTests {
    @TestConfiguration(proxyBeanMethods=false) @EnableScheduling @EnableConfigurationProperties(DefenseMaintenanceProperties.class)
    static class ScheduledHost {
        @Bean CountDownLatch completed(){return new CountDownLatch(1);}
        @Bean DeploymentWork work(){return new SecurityRetentionTests.Admission();}
        @Bean TenantWorkQuery directory(){return (after,limit)->List.of();}
        @Bean MeterRegistry meters(){return new SimpleMeterRegistry();}
        @Bean SecurityEventStore store(CountDownLatch completed){var store=mock(SecurityEventStore.class);when(store.prunePlatform()).thenAnswer(call->{completed.countDown();return 0;});return store;}
        @Bean SecurityRetentionService service(DefenseMaintenanceProperties settings,DeploymentWork work,TenantWorkQuery directory,SecurityEventStore store,MeterRegistry meters){
            return new SecurityRetentionService(settings,work,directory,store,meters);
        }
    }
    @Test void springFixedDelayInvokesActualDedicatedWorkerWithConfiguredDuration(){
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(ScheduledHost.class)
                .withPropertyValues("sma.defense.maintenance.enabled=true","sma.defense.maintenance.interval=1s","sma.defense.maintenance.tenant-batch=1")
                .run(context->{assertThat(context).hasNotFailed();assertThat(context.getBean(CountDownLatch.class).await(10,TimeUnit.SECONDS)).isTrue();});
    }
}
