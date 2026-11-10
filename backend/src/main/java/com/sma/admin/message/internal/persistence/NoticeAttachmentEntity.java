package com.sma.admin.message.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.*;
@Getter @Setter @TableName("message_notice_attachment")
public class NoticeAttachmentEntity extends TenantEntity {
 private Long noticeId;private Long fileId;
}
