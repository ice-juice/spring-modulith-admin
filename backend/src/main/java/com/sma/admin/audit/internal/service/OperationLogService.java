package com.sma.admin.audit.internal.service;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.audit.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class OperationLogService {
 private final OperationLogMapper mapper;private final MessageSource messages;private final JsonMapper json;
 public OperationLogService(OperationLogMapper mapper,MessageSource messages,JsonMapper json){this.mapper=mapper;this.messages=messages;this.json=json;}
 public record View(Long id,Long userId,String username,String action,String message,String objectId,String clientPath,String requestPath,int resultCode,String error,
  long durationMs,String ip,Instant occurredAt){}
 public PageResult<View> page(PageQuery page,String username,String action,Boolean success,Instant from,Instant to){
  var query=new LambdaQueryWrapper<OperationLogEntity>().eq(OperationLogEntity::getPortal,CurrentUser.portal()).orderByDesc(OperationLogEntity::getOccurredAt);
  if(username!=null&&!username.isBlank())query.like(OperationLogEntity::getUsername,username);if(action!=null&&!action.isBlank())query.eq(OperationLogEntity::getAction,action);
  if(success!=null){if(success)query.eq(OperationLogEntity::getResultCode,0);else query.ne(OperationLogEntity::getResultCode,0);}
  if(from!=null)query.ge(OperationLogEntity::getOccurredAt,from);if(to!=null)query.lt(OperationLogEntity::getOccurredAt,to);
  return PageResult.of(mapper.selectPage(page.toPage(Map.of("occurredAt","occurred_at","durationMs","duration_ms")),query),row->new View(row.getId(),row.getUserId(),row.getUsername(),row.getAction(),
   messages.getMessage(row.getMessageKey(),json.readValue(row.getMessageArgs(),String[].class),row.getDefaultMessage(),LocaleContextHolder.getLocale()),
   row.getObjectId(),row.getClientPath(),row.getRequestPath(),row.getResultCode(),row.getErrorKey(),row.getDurationMs(),row.getIp(),row.getOccurredAt()));
 }
}
