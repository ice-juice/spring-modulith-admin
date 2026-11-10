package com.sma.admin.message.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.*;
@Getter @Setter @TableName("message_private")
public class PrivateMessageEntity extends TenantEntity {
 private String portal;private Long senderId;private Long recipientId;private String senderName;private String recipientName;private String content;private Instant readAt;
}
