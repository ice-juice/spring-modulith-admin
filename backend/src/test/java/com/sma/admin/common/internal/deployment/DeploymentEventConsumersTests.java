package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static com.sma.admin.support.DeploymentWorkFixture.host;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.audit.internal.persistence.*;
import com.sma.admin.auth.SessionEndedEvent;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.OperationAuditEvent;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.UserChangedEvent;
import com.sma.admin.message.*;
import com.sma.admin.message.internal.service.SocketSignalStore;
import com.sma.admin.monitor.internal.service.PublicationService;
import com.sma.admin.support.*;

@IntegrationTest
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class DeploymentEventConsumersTests {
    private static final long TENANT=97261,USER=71;
    @Autowired DeploymentWorkCoordinator work;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EventPublicationRepository repository;
    @Autowired PublicationService publications;
    @Autowired OperationLogMapper audits;
    @Autowired SessionMapper sessions;
    @Autowired SocketSignalStore signals;

    @Test void allOtherProductionListenersRetainRejectedPublicationsAndReplayIntoTheirOwnDomains(){
        long session=job(()->new TransactionTemplate(transactions).execute(status->{
            var row=new SessionEntity();row.setUserId(USER);row.setPortal("admin");row.setTokenHash(UUID.randomUUID().toString().replace("-","").repeat(2));
            row.setCredentialVersion(0L);row.setExpiresAt(Instant.now().plusSeconds(3600));row.setLastSeenAt(Instant.now());row.setLocked(false);sessions.insert(row);return row.getId();
        }));
        var operation=new OperationAuditEvent(UUID.randomUUID().toString(),TENANT,USER,null,"event-fixture","admin","test","common.error.bad-request",
                "1",null,null,0,null,1,"127.0.0.1","fixture",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        var payloads=List.of(operation,new UserChangedEvent(TENANT,USER,"update",1),
                new NoticePublishedEvent(TENANT,"admin",11,Set.of(USER)),new TodoChangedEvent(TENANT,"admin",12,USER,"update"),
                new PrivateMessageSentEvent(TENANT,"admin",13,USER,72),new SessionEndedEvent(TENANT,USER,14,"FORCED"));
        try{
            AvailabilityChangeEvent.publish(events,this,ReadinessState.REFUSING_TRAFFIC);
            for(var payload:payloads)host(()->new TransactionTemplate(transactions).executeWithoutResult(status->events.publishEvent(payload)));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(()->{
                for(var payload:payloads)assertThat(find(payload,EventPublication.Status.FAILED)).isPresent();
            });
            assertThat(job(()->sessions.selectById(session))).isNotNull();assertThat(auditCount(operation)).isZero();
            assertThat(job(()->signals.read("admin",Set.of(USER)))).isEmpty();assertThat(work.snapshot().activeWork()).isZero();
            var ids=payloads.stream().map(payload->find(payload,EventPublication.Status.FAILED).orElseThrow().getIdentifier()).toList();
            AvailabilityChangeEvent.publish(events,this,ReadinessState.ACCEPTING_TRAFFIC);
            for(var id:ids)Users.as(Users.principal(USER,TENANT,DataAccess.unrestricted()),()->{publications.retry(id);return null;});
            await().atMost(Duration.ofSeconds(15)).untilAsserted(()->{
                for(var payload:payloads)assertThat(find(payload,EventPublication.Status.COMPLETED)).isPresent();
                assertThat(job(()->sessions.selectById(session))).isNull();assertThat(auditCount(operation)).isOne();
                assertThat(job(()->signals.read("admin",Set.of(USER)))).extracting(SocketSignalStore.Signal::type)
                        .contains("notice","todo","private-message","session-ended");
                assertThat(work.snapshot().activeWork()).isZero();
            });
            for(int i=0;i<payloads.size();i++)assertThat(find(payloads.get(i),EventPublication.Status.COMPLETED).orElseThrow().getIdentifier()).isEqualTo(ids.get(i));
        }finally{AvailabilityChangeEvent.publish(events,this,ReadinessState.ACCEPTING_TRAFFIC);}
    }
    private long auditCount(OperationAuditEvent event){return Users.as(Users.principal(USER,TENANT,DataAccess.unrestricted()),
            ()->audits.selectCount(new LambdaQueryWrapper<OperationLogEntity>().eq(OperationLogEntity::getEventId,event.eventId())));}
    private Optional<TargetEventPublication> find(Object event,EventPublication.Status state){return repository.findByStatus(state).stream().filter(row->row.getEvent().equals(event)).findFirst();}
    private <T>T job(Supplier<T> action){return ActorContext.withinJob(TENANT,"message-signal:event-test",action);}
}
