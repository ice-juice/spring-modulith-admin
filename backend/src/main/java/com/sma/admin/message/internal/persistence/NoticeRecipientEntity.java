package com.sma.admin.message.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.*;
@Getter @Setter @TableName("message_notice_recipient")
public class NoticeRecipientEntity extends TenantEntity {
 private Long noticeId;private Long userId;private String userName;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private Instant readAt;
}
