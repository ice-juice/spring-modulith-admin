package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import static com.sma.admin.support.DeploymentWorkFixture.host;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.*;
import org.springframework.modulith.events.support.CompletionRegisteringAdvisor;
import org.springframework.scheduling.annotation.AsyncAnnotationAdvisor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.audit.internal.persistence.*;
import com.sma.admin.audit.internal.service.LoginLogListener;
import com.sma.admin.auth.LoginEvent;
import com.sma.admin.common.security.*;
import com.sma.admin.monitor.internal.service.PublicationService;
import com.sma.admin.support.*;

@IntegrationTest
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class DeploymentEventMysqlTests {
    @Autowired DeploymentWorkCoordinator work;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EventPublicationRepository repository;
    @Autowired PublicationService publications;
    @Autowired LoginHistoryMapper history;
    @Autowired LoginLogListener listener;
    @MockitoSpyBean EventPublicationRegistry registry;

    @Test void realFrameworkRefusesWithoutWritingRetriesOriginalPublicationAndTracksCompletionDuringDrain()throws Exception{
        assertProxyOrder();
        var denied=event("denied");var inFlight=event("in-flight");var afterDrain=event("after-drain");
        var reachedCompletion=new CountDownLatch(1);var releaseCompletion=new CountDownLatch(1);
        doAnswer(call->{
            if(call.getArgument(0).equals(inFlight)){
                assertThat(Thread.currentThread().getName()).startsWith("sma-events-");
                assertThat(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).isNull();
                assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
                // Registry owns a separate completion transaction; the listener transaction has already committed.
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(work.snapshot().activeWork()).isOne();reachedCompletion.countDown();
                if(!releaseCompletion.await(10,TimeUnit.SECONDS))throw new AssertionError("Completion gate not released");
            }
            return call.callRealMethod();
        }).when(registry).markCompleted(any(),any());
        try{
            AvailabilityChangeEvent.publish(events,this,ReadinessState.REFUSING_TRAFFIC);
            publish(denied);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(find(denied,EventPublication.Status.FAILED)).isPresent());
            var id=find(denied,EventPublication.Status.FAILED).orElseThrow().getIdentifier();
            assertThat(rows(denied)).isZero();assertThat(work.snapshot().activeWork()).isZero();
            AvailabilityChangeEvent.publish(events,this,ReadinessState.ACCEPTING_TRAFFIC);
            var actor=Users.principal(71,97260,DataAccess.unrestricted());
            Users.as(actor,()->{publications.retry(id);return null;});
            await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(find(denied,EventPublication.Status.COMPLETED)).isPresent());
            assertThat(find(denied,EventPublication.Status.COMPLETED).orElseThrow().getIdentifier()).isEqualTo(id);
            assertThat(rows(denied)).isOne();await().atMost(Duration.ofSeconds(3)).until(()->work.snapshot().activeWork()==0);

            publish(inFlight);assertThat(reachedCompletion.await(5,TimeUnit.SECONDS)).isTrue();
            // Domain transaction already committed; framework completion is deliberately still in flight.
            assertThat(rows(inFlight)).isOne();assertThat(find(inFlight,EventPublication.Status.COMPLETED)).isEmpty();
            assertThat(work.snapshot().activeWork()).isOne();assertThat(host(work::beginDrain)).isTrue();
            publish(afterDrain);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(()->assertThat(find(afterDrain,EventPublication.Status.FAILED)).isPresent());
            assertThat(rows(afterDrain)).isZero();assertThat(work.snapshot().activeWork()).isOne();
            releaseCompletion.countDown();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(()->{
                assertThat(find(inFlight,EventPublication.Status.COMPLETED)).isPresent();assertThat(work.snapshot().activeWork()).isZero();
            });
            assertThat(rows(inFlight)).isOne();assertThat(find(afterDrain,EventPublication.Status.FAILED)).isPresent();
        }finally{releaseCompletion.countDown();AvailabilityChangeEvent.publish(events,this,ReadinessState.ACCEPTING_TRAFFIC);}
    }
    private void assertProxyOrder(){
        assertThat(listener).isInstanceOf(Advised.class);var advisors=((Advised)listener).getAdvisors();
        int async=-1,lifetime=-1,completion=-1,admission=-1,transaction=-1;
        for(int i=0;i<advisors.length;i++){
            var advisor=advisors[i];
            if(advisor instanceof AsyncAnnotationAdvisor)async=i;
            if(advisor instanceof CompletionRegisteringAdvisor)completion=i;
            if(advisor instanceof org.springframework.core.Ordered ordered){
                if(ordered.getOrder()==Integer.MIN_VALUE+5)lifetime=i;
                if(ordered.getOrder()==Integer.MIN_VALUE+20)admission=i;
            }
            if(advisor.getAdvice() instanceof TransactionInterceptor)transaction=i;
        }
        assertThat(async).isGreaterThanOrEqualTo(0);assertThat(lifetime).isGreaterThan(async);
        assertThat(completion).isGreaterThan(lifetime);assertThat(admission).isGreaterThan(completion);assertThat(transaction).isGreaterThan(admission);
    }
    private void publish(LoginEvent event){host(()->new TransactionTemplate(transactions).executeWithoutResult(status->events.publishEvent(event)));}
    private long rows(LoginEvent event){return TenantScope.within(event.tenantId(),()->history.selectCount(new LambdaQueryWrapper<LoginLogEntity>().eq(LoginLogEntity::getEventId,event.eventId())));}
    private Optional<TargetEventPublication> find(LoginEvent event,EventPublication.Status state){return repository.findByStatus(state).stream().filter(row->row.getEvent().equals(event)).findFirst();}
    private LoginEvent event(String name){return new LoginEvent(UUID.randomUUID().toString(),97260,null,null,name,"admin","PASSWORD_FAILURE","127.0.0.1","synthetic",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));}
}
