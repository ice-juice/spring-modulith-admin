package com.sma.admin.monitor;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ApplicationEventPublisher;
import com.sma.admin.auth.LoginEvent;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.message.NoticePublishedEvent;
import com.sma.admin.monitor.internal.service.PublicationService;
import com.sma.admin.support.*;

@IntegrationTest
class PublicationTests {
 @Autowired EventPublicationRepository repository;@Autowired PublicationService service;@Autowired ApplicationEventPublisher events;@Autowired PlatformTransactionManager transactions;
 @Test void failedPublicationRetriesThroughActualListenerAndCannotCrossTenants(){
  Users.as(Users.principal(101,271,DataAccess.unrestricted()),()->{
   var original=login("initial");new TransactionTemplate(transactions).execute(status->{events.publishEvent(original);return null;});
   org.awaitility.Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(10)).until(()->repository.findCompletedPublications().stream().anyMatch(p->p.getEvent().equals(original)));
   var target=repository.findCompletedPublications().stream().filter(p->p.getEvent().equals(original)).findFirst().orElseThrow().getTargetIdentifier();
   var earlier=repository.create(TargetEventPublication.of(new LoginEvent(UUID.randomUUID().toString(),272,101L,null,"earlier-foreign","admin","SUCCESS","127.0.0.1","test",Instant.now()),target,Instant.now().minusSeconds(120)));
   repository.markFailed(earlier.getIdentifier());
   var failed=repository.create(TargetEventPublication.of(login("retry"),target,Instant.now().minusSeconds(60)));repository.markFailed(failed.getIdentifier());
   assertThat(service.page(new PageQuery(1,20,null,null),"FAILED").items()).anySatisfy(row->assertThat(row.id()).isEqualTo(failed.getIdentifier().toString()));
   Users.as(Users.principal(101,272,DataAccess.unrestricted()),()->{
    assertThat(service.page(new PageQuery(1,20,null,null),"FAILED").items()).noneSatisfy(row->assertThat(row.id()).isEqualTo(failed.getIdentifier().toString()));
    assertThatThrownBy(()->service.retry(failed.getIdentifier())).isInstanceOf(BizException.class);return null;
   });
   service.retry(failed.getIdentifier());
   org.awaitility.Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(10)).until(()->repository.findCompletedPublications().stream().anyMatch(p->p.getIdentifier().equals(failed.getIdentifier())));
   assertThat(repository.findByStatus(EventPublication.Status.FAILED)).anySatisfy(row->assertThat(row.getIdentifier()).isEqualTo(earlier.getIdentifier()));
   assertThatThrownBy(()->service.retry(failed.getIdentifier())).isInstanceOf(BizException.class);return null;
  });
 }
 @Test void recipientSnapshotLargerThanFourThousandCharactersIsPersisted(){
  Set<Long> ids=new HashSet<>();for(int i=0;i<500;i++)ids.add(Long.MAX_VALUE-i);
  var event=new NoticePublishedEvent(273,"admin",1,ids);
  var stored=repository.create(TargetEventPublication.of(event,PublicationTargetIdentifier.of("large-payload-test")));
  assertThat(repository.findIncompletePublications()).anySatisfy(row->{assertThat(row.getIdentifier()).isEqualTo(stored.getIdentifier());assertThat(row.getEvent()).isEqualTo(event);});
  repository.markCompleted(stored.getIdentifier(),Instant.now());
 }
 private LoginEvent login(String prefix){return new LoginEvent(UUID.randomUUID().toString(),271,101L,10L,prefix+"-retry-user","admin","SUCCESS","127.0.0.1","test",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));}
}
