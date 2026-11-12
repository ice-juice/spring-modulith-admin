package com.sma.admin.audit.internal.service;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.audit.internal.persistence.*;

@Component
public class OperationLogListener {
 private final OperationLogMapper mapper;private final MessageSource messages;private final JsonMapper json;
 public OperationLogListener(OperationLogMapper mapper,MessageSource messages,JsonMapper json){this.mapper=mapper;this.messages=messages;this.json=json;}
 @org.springframework.scheduling.annotation.Async("deploymentEventExecutor") @ApplicationModuleListener public void on(OperationAuditEvent event){
  TenantScope.within(event.tenantId(),()->{
   String[] args={event.objectId()==null?"—":event.objectId()};
   var row=new OperationLogEntity();row.setEventId(event.eventId());row.setUserId(event.userId());row.setDeptId(event.departmentId());row.setUsername(event.username());row.setPortal(event.portal());
   row.setAction(event.action());row.setMessageKey(event.messageKey());row.setMessageArgs(json.writeValueAsString(args));
   row.setDefaultMessage(messages.getMessage(event.messageKey(),args,event.action(),Locale.CHINA));row.setObjectId(event.objectId());row.setClientPath(event.clientPath());row.setRequestPath(event.requestPath());
   row.setResultCode(event.resultCode());row.setErrorKey(event.error());row.setDurationMs(event.durationMs());row.setIp(event.ip());row.setUserAgent(event.userAgent());row.setOccurredAt(event.occurredAt());
   try{mapper.insert(row);}catch(DuplicateKeyException duplicate){/* Idempotent event replay. */}return null;
  });
 }
}
