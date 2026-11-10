package com.sma.admin.message.internal.service;
import java.time.Instant;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.AudienceQuery;
import com.sma.admin.message.*;
import com.sma.admin.message.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class TodoService {
 private static final Set<Integer> OPEN=Set.of(1,2,5);
 private final TodoMapper todos;private final ManagedTodoMapper managed;private final AudienceQuery audience;private final ApplicationEventPublisher events;private final com.sma.admin.iam.DirectoryQuery directory;
 public TodoService(TodoMapper todos,ManagedTodoMapper managed,AudienceQuery audience,ApplicationEventPublisher events,com.sma.admin.iam.DirectoryQuery directory){this.todos=todos;this.managed=managed;this.audience=audience;this.events=events;this.directory=directory;}
 public record Source(@Size(max=64) String system,@Size(max=64) String messageId,@Size(max=64) String requestId,@Size(max=512) String url,@Size(max=64) String senderKey,@Size(max=64) String receiverKey){}
 public record Input(@NotBlank @Size(max=255) String title,@Size(max=30000) String content,@Size(max=512) String url,
  @NotNull @Min(1) @Max(4) Integer type,@NotNull @Min(1) @Max(3) Integer priority,@NotNull Instant dueAt,Long userId,@jakarta.validation.Valid Source source){}
 public record View(Long id,Long userId,String userName,String title,String content,String url,int type,int priority,int state,Instant dueAt,Instant completedAt,Instant cancelledAt,Instant readAt,
  Source source,Long createdBy,Instant createdAt,Instant updatedAt){}
 private LambdaQueryWrapper<TodoEntity> inPortal(){return new LambdaQueryWrapper<TodoEntity>().eq(TodoEntity::getPortal,CurrentUser.portal());}
 public PageResult<View> page(PageQuery page,String keyword,Integer state,Integer type,Integer priority,Boolean active,String dateField,Instant from,Instant to,boolean management){
  var query=inPortal().orderByAsc(TodoEntity::getDueAt).orderByDesc(TodoEntity::getId);
  if(!management)query.eq(TodoEntity::getUserId,CurrentUser.require().userId());
  query.like(keyword!=null&&!keyword.isBlank(),TodoEntity::getTitle,keyword).eq(state!=null,TodoEntity::getState,state)
   .eq(type!=null,TodoEntity::getType,type).eq(priority!=null,TodoEntity::getPriority,priority).in(Boolean.TRUE.equals(active),TodoEntity::getState,OPEN);
  com.baomidou.mybatisplus.core.toolkit.support.SFunction<TodoEntity,?> column=switch(dateField==null?"dueAt":dateField){
   case "createdAt"->TodoEntity::getCreatedAt;case "updatedAt"->TodoEntity::getUpdatedAt;case "dueAt"->TodoEntity::getDueAt;
   case "completedAt"->TodoEntity::getCompletedAt;case "cancelledAt"->TodoEntity::getCancelledAt;default->throw new BizException(CommonErrorCode.BAD_REQUEST);
  };
  if(from!=null&&to!=null&&!from.isBefore(to))throw new BizException(CommonErrorCode.BAD_REQUEST);
  var paging=page.<TodoEntity>toPage(Map.of("dueAt","due_at","createdAt","created_at","priority","priority"));
  query.ge(from!=null,column,from).lt(to!=null,column,to);
  var result=management?managed.selectPage(paging,query):todos.selectPage(paging,query);
  var names=directory.names(result.getRecords().stream().map(TodoEntity::getUserId).collect(java.util.stream.Collectors.toSet()));
  return PageResult.of(result,row->view(row,names.get(row.getUserId())));
 }
 public java.util.List<OptionItem> recipients(String keyword){return audience.options("USER",keyword);}
 private TodoEntity own(long id,boolean lock){
  var query=inPortal().eq(TodoEntity::getId,id).eq(TodoEntity::getUserId,CurrentUser.require().userId());if(lock)query.last("FOR UPDATE");
  var row=todos.selectOne(query);if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row;
 }
 public View get(long id){return view(own(id,false));}
 @Transactional public View save(Long id,Input input,boolean assign){
  var row=id==null?new TodoEntity():own(id,true);
  if(id!=null&&!OPEN.contains(row.getState()))throw new BizException(MessageErrorCode.INVALID_STATE);
  long userId=input.userId()==null?CurrentUser.require().userId():input.userId();
  if(!assign&&userId!=CurrentUser.require().userId())throw new BizException(CommonErrorCode.FORBIDDEN);
  if(id!=null&&userId!=row.getUserId())throw new BizException(CommonErrorCode.BAD_REQUEST);
  if(assign)audience.resolve("USER",Set.of(userId));
  boolean hasContent=input.type()==2||input.type()==4,hasLink=input.type()==3||input.type()==4;
  if((hasContent&&(input.content()==null||input.content().isBlank()))||(hasLink&&(input.url()==null||input.url().isBlank())))throw new BizException(CommonErrorCode.BAD_REQUEST);
  row.setTitle(input.title().trim());row.setContent(hasContent?input.content():null);row.setUrl(hasLink?MessageLinks.validate(input.url(),true):null);
  row.setType(input.type());row.setPriority(input.priority());row.setDueAt(input.dueAt());
  var source=input.source();row.setSourceSystem(source==null?null:source.system());row.setSourceMessageId(source==null?null:source.messageId());
  row.setSourceRequestId(source==null?null:source.requestId());row.setSourceUrl(source==null?null:MessageLinks.validate(source.url(),false));
  row.setSenderKey(source==null?null:source.senderKey());row.setReceiverKey(source==null?null:source.receiverKey());
  if(id==null){row.setPortal(CurrentUser.portal());row.setDeptId(CurrentUser.require().departmentId());row.setUserId(userId);row.setState(1);todos.insert(row);}else todos.updateById(row);
  events.publishEvent(new TodoChangedEvent(CurrentUser.tenantId(),CurrentUser.portal(),row.getId(),row.getUserId(),id==null?"CREATED":"UPDATED"));return view(row);
 }
 @Transactional public View state(long id,int state){
  if(state<1||state>5)throw new BizException(CommonErrorCode.BAD_REQUEST);
  var row=own(id,true);if(row.getState()==state)return view(row);
  if(!OPEN.contains(row.getState()))throw new BizException(MessageErrorCode.INVALID_STATE);
  row.setState(state);if(state==3)row.setCompletedAt(Instant.now());if(state==4)row.setCancelledAt(Instant.now());todos.updateById(row);
  events.publishEvent(new TodoChangedEvent(CurrentUser.tenantId(),CurrentUser.portal(),id,row.getUserId(),"STATE_CHANGED"));return view(row);
 }
 @Transactional public void batchState(Set<Long> ids,int state){for(long id:ids.stream().sorted().toList())state(id,state);}
 @Transactional public void deleteMany(Set<Long> ids){for(long id:ids.stream().sorted().toList()){own(id,true);todos.deleteById(id);}}
 @Transactional public View read(long id){var row=own(id,true);if(row.getReadAt()==null){row.setReadAt(Instant.now());todos.updateById(row);}return view(row);}
 public long pendingCount(){return todos.selectCount(inPortal().eq(TodoEntity::getUserId,CurrentUser.require().userId()).in(TodoEntity::getState,OPEN));}
 private View view(TodoEntity row){return view(row,directory.names(Set.of(row.getUserId())).get(row.getUserId()));}
 private View view(TodoEntity row,String name){return new View(row.getId(),row.getUserId(),name==null?Long.toString(row.getUserId()):name,row.getTitle(),row.getContent(),row.getUrl(),row.getType(),row.getPriority(),row.getState(),row.getDueAt(),row.getCompletedAt(),row.getCancelledAt(),row.getReadAt(),
  new Source(row.getSourceSystem(),row.getSourceMessageId(),row.getSourceRequestId(),row.getSourceUrl(),row.getSenderKey(),row.getReceiverKey()),row.getCreatedBy(),row.getCreatedAt(),row.getUpdatedAt());}
}
