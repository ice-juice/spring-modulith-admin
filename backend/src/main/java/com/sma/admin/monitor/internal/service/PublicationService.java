package com.sma.admin.monitor.internal.service;
import java.time.*;
import java.util.*;
import org.springframework.modulith.events.*;
import org.springframework.modulith.events.core.*;
import org.springframework.stereotype.Service;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;

@Service
public class PublicationService {
 private final EventPublicationRepository repository;private final FailedEventPublications failures;
 public PublicationService(EventPublicationRepository repository,FailedEventPublications failures){this.repository=repository;this.failures=failures;}
 public record View(String id,String eventType,String listener,String status,int attempts,Instant publishedAt,Instant lastResubmissionAt,Instant completedAt){}
 public PageResult<View> page(PageQuery page,String status){
  var paging=page.toPage(Map.of());EventPublication.Status state;
  try{state=EventPublication.Status.valueOf(status==null?"FAILED":status);}catch(IllegalArgumentException invalid){throw new BizException(CommonErrorCode.BAD_REQUEST);}
  var rows=repository.findByStatus(state).stream().filter(this::sameTenant).sorted(Comparator.comparing(EventPublication::getPublicationDate).reversed()).toList();
  long offset=(paging.getCurrent()-1)*paging.getSize();var items=rows.stream().skip(offset).limit(paging.getSize()).map(this::view).toList();
  return new PageResult<>(items,rows.size(),paging.getCurrent(),paging.getSize());
 }
 public synchronized void retry(UUID id){
  if(repository.findByStatus(EventPublication.Status.FAILED).stream().noneMatch(publication->publication.getIdentifier().equals(id)&&sameTenant(publication)))throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
  long tenant=CurrentUser.tenantId();
  failures.resubmit(ResubmissionOptions.defaults().withBatchSize(1).withMaxInFlight(1).withMinAge(Duration.ZERO)
   .withFilter(publication->publication.getIdentifier().equals(id)&&tenant(publication.getEvent())==tenant));
 }
 private boolean sameTenant(EventPublication publication){return tenant(publication.getEvent())==CurrentUser.tenantId();}
 private long tenant(Object event){
  if(event==null||!event.getClass().isRecord())return -1;
  try{return event.getClass().getMethod("tenantId").invoke(event) instanceof Number id?id.longValue():-1;}
  catch(ReflectiveOperationException invalid){return -1;}
 }
 private View view(TargetEventPublication publication){return new View(publication.getIdentifier().toString(),publication.getEvent().getClass().getSimpleName(),publication.getTargetIdentifier().getValue(),
  publication.getStatus().name(),publication.getCompletionAttempts(),publication.getPublicationDate(),publication.getLastResubmissionDate(),publication.getCompletionDate().orElse(null));}
}
