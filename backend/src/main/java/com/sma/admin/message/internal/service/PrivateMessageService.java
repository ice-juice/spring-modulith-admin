package com.sma.admin.message.internal.service;
import java.time.Instant;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.*;
import com.sma.admin.message.PrivateMessageSentEvent;
import com.sma.admin.message.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class PrivateMessageService {
 private final PrivateMessageMapper messages;private final AccountQuery accounts;private final DirectoryQuery directory;private final ApplicationEventPublisher events;
 public PrivateMessageService(PrivateMessageMapper messages,AccountQuery accounts,DirectoryQuery directory,ApplicationEventPublisher events){this.messages=messages;this.accounts=accounts;this.directory=directory;this.events=events;}
 public record Input(@NotNull Long recipientId,@NotBlank @Size(max=10000) String content){}
 public record View(Long id,Long senderId,Long recipientId,String senderName,String recipientName,String content,Instant createdAt,Instant readAt){}
 public List<OptionItem> recipients(String keyword){return directory.search(keyword);}
 private LambdaQueryWrapper<PrivateMessageEntity> inPortal(){return new LambdaQueryWrapper<PrivateMessageEntity>().eq(PrivateMessageEntity::getPortal,CurrentUser.portal());}
 @Transactional public View send(Input input){
  var recipient=accounts.findById(input.recipientId()).filter(user->user.enabled()&&user.portal().equals(CurrentUser.portal())).orElseThrow(()->new BizException(CommonErrorCode.DATA_NOT_FOUND));
  var sender=accounts.findById(CurrentUser.require().userId()).filter(AccountView::enabled).orElseThrow(()->new BizException(CommonErrorCode.UNAUTHORIZED));
  var row=new PrivateMessageEntity();row.setPortal(CurrentUser.portal());row.setSenderId(sender.id());row.setRecipientId(recipient.id());row.setSenderName(sender.name());row.setRecipientName(recipient.name());row.setContent(input.content().trim());
  messages.insert(row);events.publishEvent(new PrivateMessageSentEvent(CurrentUser.tenantId(),CurrentUser.portal(),row.getId(),sender.id(),recipient.id()));return view(row);
 }
 public PageResult<View> page(PageQuery page,String box,Long peerId,Boolean unread){
  var query=inPortal().orderByDesc(PrivateMessageEntity::getCreatedAt).orderByDesc(PrivateMessageEntity::getId);long userId=CurrentUser.require().userId();
  switch(box==null?"inbox":box){
   case "inbox"->query.eq(PrivateMessageEntity::getRecipientId,userId).eq(peerId!=null,PrivateMessageEntity::getSenderId,peerId);
   case "outbox"->query.eq(PrivateMessageEntity::getSenderId,userId).eq(peerId!=null,PrivateMessageEntity::getRecipientId,peerId);
   case "conversation"->{if(peerId==null)throw new BizException(CommonErrorCode.BAD_REQUEST);query.and(q->q
    .eq(PrivateMessageEntity::getSenderId,userId).eq(PrivateMessageEntity::getRecipientId,peerId).or()
    .eq(PrivateMessageEntity::getSenderId,peerId).eq(PrivateMessageEntity::getRecipientId,userId));}
   default->throw new BizException(CommonErrorCode.BAD_REQUEST);
  }
  if(Boolean.TRUE.equals(unread))query.eq(PrivateMessageEntity::getRecipientId,userId).isNull(PrivateMessageEntity::getReadAt);
  return PageResult.of(messages.selectPage(page.toPage(Map.of()),query),this::view);
 }
 public View get(long id){return view(require(id));}
 private PrivateMessageEntity require(long id){
  long userId=CurrentUser.require().userId();var row=messages.selectOne(inPortal().eq(PrivateMessageEntity::getId,id)
   .and(q->q.eq(PrivateMessageEntity::getSenderId,userId).or().eq(PrivateMessageEntity::getRecipientId,userId)));
  if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row;
 }
 @Transactional public View read(long id){
  var row=require(id);if(row.getRecipientId()!=CurrentUser.require().userId())throw new BizException(CommonErrorCode.FORBIDDEN);
  messages.update(null,new LambdaUpdateWrapper<PrivateMessageEntity>().eq(PrivateMessageEntity::getId,id).isNull(PrivateMessageEntity::getReadAt)
   .set(PrivateMessageEntity::getReadAt,Instant.now()).set(PrivateMessageEntity::getUpdatedAt,Instant.now()).set(PrivateMessageEntity::getUpdatedBy,CurrentUser.userId()));return get(id);
 }
 @Transactional public int readAll(){return messages.update(null,new LambdaUpdateWrapper<PrivateMessageEntity>()
  .eq(PrivateMessageEntity::getPortal,CurrentUser.portal()).eq(PrivateMessageEntity::getRecipientId,CurrentUser.require().userId()).isNull(PrivateMessageEntity::getReadAt)
  .set(PrivateMessageEntity::getReadAt,Instant.now()).set(PrivateMessageEntity::getUpdatedAt,Instant.now()).set(PrivateMessageEntity::getUpdatedBy,CurrentUser.userId()));}
 public long unreadCount(){return messages.selectCount(inPortal().eq(PrivateMessageEntity::getRecipientId,CurrentUser.require().userId()).isNull(PrivateMessageEntity::getReadAt));}
 private View view(PrivateMessageEntity row){return new View(row.getId(),row.getSenderId(),row.getRecipientId(),row.getSenderName(),row.getRecipientName(),row.getContent(),row.getCreatedAt(),row.getReadAt());}
}
