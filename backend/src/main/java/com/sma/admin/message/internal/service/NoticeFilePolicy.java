package com.sma.admin.message.internal.service;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.persistence.*;

@Component
public class NoticeFilePolicy implements FileAccessPolicy {
 private final NoticeAttachmentMapper attachments;private final NoticeLookupMapper lookup;private final NoticeMapper notices;private final NoticeRecipientMapper recipients;
 public NoticeFilePolicy(NoticeAttachmentMapper attachments,NoticeLookupMapper lookup,NoticeMapper notices,NoticeRecipientMapper recipients){this.attachments=attachments;this.lookup=lookup;this.notices=notices;this.recipients=recipients;}
 @Override public boolean inUse(long fileId){return attachments.selectCount(new LambdaQueryWrapper<NoticeAttachmentEntity>().eq(NoticeAttachmentEntity::getFileId,fileId))>0;}
 @Override public boolean canRead(long fileId,SmaPrincipal user){
  for(var link:attachments.selectList(new LambdaQueryWrapper<NoticeAttachmentEntity>().eq(NoticeAttachmentEntity::getFileId,fileId))){
   var row=lookup.selectById(link.getNoticeId());if(row==null||!row.getPortal().equals(user.portal()))continue;
   if(row.getCreatedBy()==user.userId())return true;
   if((user.superAdmin()||user.permissions().contains("message:notice:view"))&&notices.selectById(row.getId())!=null)return true;
   if(recipients.selectCount(new LambdaQueryWrapper<NoticeRecipientEntity>().eq(NoticeRecipientEntity::getNoticeId,row.getId()).eq(NoticeRecipientEntity::getUserId,user.userId()))>0)return true;
  }return false;
 }
}
