package com.sma.admin.common.internal.service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.OperationAuditEvent;
@Service public class OperationAuditRecorder {
 private final ApplicationEventPublisher events;
 public OperationAuditRecorder(ApplicationEventPublisher events){this.events=events;}
 @Transactional public void record(OperationAuditEvent event){events.publishEvent(event);}
}
