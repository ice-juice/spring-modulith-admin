package com.sma.admin.message.internal.service;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.validation.constraints.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.AudienceQuery;
import com.sma.admin.file.*;
import com.sma.admin.message.*;
import com.sma.admin.message.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class NoticeService {
 private final NoticeMapper notices;private final NoticeLookupMapper lookup;private final NoticeRecycleMapper recycle;
 private final NoticeRecipientMapper recipients;private final NoticeAttachmentMapper attachments;private final AudienceQuery audience;
 private final FileQuery files;private final ApplicationEventPublisher events;
 public NoticeService(NoticeMapper notices,NoticeLookupMapper lookup,NoticeRecycleMapper recycle,NoticeRecipientMapper recipients,
  NoticeAttachmentMapper attachments,AudienceQuery audience,FileQuery files,ApplicationEventPublisher events){
  this.notices=notices;this.lookup=lookup;this.recycle=recycle;this.recipients=recipients;this.attachments=attachments;this.audience=audience;this.files=files;this.events=events;
 }
 public record Input(@NotBlank @Size(max=255) String title,@NotBlank @Size(max=200000) String content,
  @NotNull @Pattern(regexp="[1-5]") String type,@NotNull @Min(1) @Max(3) Integer priority,
  @NotNull @Pattern(regexp="ALL|ROLE|DEPARTMENT|POST|USER") String audienceType,@NotNull @Size(max=1000) Set<@NotNull Long> audienceIds,
  @NotNull @Size(max=20) Set<@NotNull Long> fileIds){}
 public record View(Long id,String title,String content,String type,int priority,String audienceType,Set<Long> audienceIds,
  Set<Long> fileIds,long recipientCount,long readCount,Long createdBy,Instant createdAt,Instant updatedAt,Instant readAt){}
 public record Reader(Long userId,String name,Instant readAt){}
 public PageResult<View> page(PageQuery page,String keyword,String type,Integer priority,String audienceType,boolean trash){
  if(trash)return PageResult.of(recycle.recycled(page.toPage(Map.of()),CurrentUser.portal()),this::view);
  var query=new LambdaQueryWrapper<NoticeEntity>().eq(NoticeEntity::getPortal,CurrentUser.portal()).orderByDesc(NoticeEntity::getId);
  query.like(keyword!=null&&!keyword.isBlank(),NoticeEntity::getTitle,keyword).eq(type!=null&&!type.isBlank(),NoticeEntity::getType,type)
   .eq(priority!=null,NoticeEntity::getPriority,priority).eq(audienceType!=null&&!audienceType.isBlank(),NoticeEntity::getAudienceType,audienceType);
  return PageResult.of(notices.selectPage(page.toPage(Map.of("createdAt","created_at","priority","priority")),query),this::view);
 }
 private NoticeEntity managed(long id,boolean lock){
  var query=new LambdaQueryWrapper<NoticeEntity>().eq(NoticeEntity::getId,id).eq(NoticeEntity::getPortal,CurrentUser.portal());if(lock)query.last("FOR UPDATE");
  var row=notices.selectOne(query);if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row;
 }
 public View get(long id){return view(managed(id,false));}
 public List<OptionItem> options(String type,String keyword){return audience.options(type,keyword);}
 @Transactional public View save(Long id,Input input){
  var members=audience.resolve(input.audienceType(),input.audienceIds());if(members.isEmpty())throw new BizException(MessageErrorCode.EMPTY_AUDIENCE);
  var row=id==null?new NoticeEntity():managed(id,true);
  Set<Long> previous=id==null?Set.of():fileIds(id);
  for(long fileId:input.fileIds().stream().sorted().toList())if(!previous.contains(fileId))files.requireOwned(fileId);
  row.setPortal(CurrentUser.portal());if(id==null)row.setDeptId(CurrentUser.require().departmentId());
  row.setTitle(input.title().trim());row.setContent(Jsoup.clean(input.content(),Safelist.basic().removeTags("img").removeProtocols("a","href","ftp","mailto").addEnforcedAttribute("a","rel","noopener noreferrer")));
  if(Jsoup.parse(row.getContent()).text().isBlank())throw new BizException(CommonErrorCode.BAD_REQUEST);
  row.setType(input.type());row.setPriority(input.priority());row.setAudienceType(input.audienceType());
  row.setAudienceIds(input.audienceIds().stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
  if(id==null)notices.insert(row);else notices.updateById(row);
  var existing=recipients.selectList(new LambdaQueryWrapper<NoticeRecipientEntity>().eq(NoticeRecipientEntity::getNoticeId,row.getId()));
  Set<Long> retained=new HashSet<>();
  for(var receiver:existing){if(members.containsKey(receiver.getUserId()))retained.add(receiver.getUserId());else recipients.deleteById(receiver.getId());}
  members.forEach((userId,name)->{if(!retained.contains(userId)){var receiver=new NoticeRecipientEntity();receiver.setNoticeId(row.getId());receiver.setUserId(userId);receiver.setUserName(name);recipients.insert(receiver);}});
  if(!previous.equals(input.fileIds())){
   attachments.delete(new LambdaQueryWrapper<NoticeAttachmentEntity>().eq(NoticeAttachmentEntity::getNoticeId,row.getId()));
   for(long fileId:input.fileIds()){var attachment=new NoticeAttachmentEntity();attachment.setNoticeId(row.getId());attachment.setFileId(fileId);attachments.insert(attachment);}
  }
  events.publishEvent(new NoticePublishedEvent(CurrentUser.tenantId(),CurrentUser.portal(),row.getId(),members.keySet()));
  return view(row);
 }
 @Transactional public void delete(long id){managed(id,true);notices.deleteById(id);}
 @Transactional public void restore(long id){if(recycle.restore(id,CurrentUser.portal(),Instant.now(),CurrentUser.userId())!=1)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);}
 @Transactional public void deleteMany(Set<Long> ids){for(long id:ids)delete(id);}
 @Transactional public void restoreMany(Set<Long> ids){for(long id:ids)restore(id);}
 public PageResult<Reader> readers(long id,PageQuery page){
  managed(id,false);return PageResult.of(recipients.selectPage(page.toPage(Map.of()),new LambdaQueryWrapper<NoticeRecipientEntity>()
   .eq(NoticeRecipientEntity::getNoticeId,id).orderByAsc(NoticeRecipientEntity::getUserId)),row->new Reader(row.getUserId(),row.getUserName(),row.getReadAt()));
 }
 private Set<Long> fileIds(long id){return attachments.selectList(new LambdaQueryWrapper<NoticeAttachmentEntity>().eq(NoticeAttachmentEntity::getNoticeId,id)).stream().map(NoticeAttachmentEntity::getFileId).collect(Collectors.toSet());}
 private NoticeRecipientEntity receiver(long id){return recipients.selectOne(new LambdaQueryWrapper<NoticeRecipientEntity>().eq(NoticeRecipientEntity::getNoticeId,id).eq(NoticeRecipientEntity::getUserId,CurrentUser.require().userId()));}
 public PageResult<View> inbox(PageQuery page,Boolean unread){
  var query=new LambdaQueryWrapper<NoticeEntity>().eq(NoticeEntity::getPortal,CurrentUser.portal())
   .inSql(NoticeEntity::getId,"SELECT notice_id FROM message_notice_recipient WHERE tenant_id="+CurrentUser.tenantId()+" AND deleted=0 AND user_id="+CurrentUser.require().userId()+(Boolean.TRUE.equals(unread)?" AND read_at IS NULL":""))
   .orderByDesc(NoticeEntity::getPriority).orderByDesc(NoticeEntity::getId);
  return PageResult.of(lookup.selectPage(page.toPage(Map.of()),query),this::view);
 }
 @Transactional public View read(long id){
  var row=lookup.selectOne(new LambdaQueryWrapper<NoticeEntity>().eq(NoticeEntity::getId,id).eq(NoticeEntity::getPortal,CurrentUser.portal()).last("FOR UPDATE"));
  if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
  var receiver=receiver(id);if(receiver==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
  recipients.update(null,new LambdaUpdateWrapper<NoticeRecipientEntity>().eq(NoticeRecipientEntity::getId,receiver.getId()).isNull(NoticeRecipientEntity::getReadAt)
   .set(NoticeRecipientEntity::getReadAt,Instant.now()).set(NoticeRecipientEntity::getUpdatedAt,Instant.now()).set(NoticeRecipientEntity::getUpdatedBy,CurrentUser.userId()));
  return view(row);
 }
 public long unreadCount(){return inbox(new PageQuery(1,1,null,null),true).total();}
 @Transactional public int readAll(){return recipients.readAll(CurrentUser.require().userId(),CurrentUser.portal(),Instant.now());}
 @Transactional public void readMany(Set<Long> ids){for(long id:ids)read(id);}
 private View view(NoticeEntity row){
  var query=new LambdaQueryWrapper<NoticeRecipientEntity>().eq(NoticeRecipientEntity::getNoticeId,row.getId());long total=recipients.selectCount(query);
  long read=recipients.selectCount(new LambdaQueryWrapper<NoticeRecipientEntity>().eq(NoticeRecipientEntity::getNoticeId,row.getId()).isNotNull(NoticeRecipientEntity::getReadAt));
  var receiver=receiver(row.getId());
  Set<Long> selected=row.getAudienceIds().isBlank()?Set.of():Arrays.stream(row.getAudienceIds().split(",")).map(Long::valueOf).collect(Collectors.toSet());
  return new View(row.getId(),row.getTitle(),row.getContent(),row.getType(),row.getPriority(),row.getAudienceType(),selected,fileIds(row.getId()),total,read,row.getCreatedBy(),row.getCreatedAt(),row.getUpdatedAt(),receiver==null?null:receiver.getReadAt());
 }
}
