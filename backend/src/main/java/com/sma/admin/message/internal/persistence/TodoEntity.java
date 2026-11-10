package com.sma.admin.message.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.*;
@Getter @Setter @TableName("message_todo")
public class TodoEntity extends TenantEntity {
 private String portal;private Long deptId;private Long userId;private String title;private Integer type;private Integer priority;private Integer state;private Instant dueAt;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String content;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String url;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private Instant completedAt;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private Instant cancelledAt;
 private Instant readAt;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String sourceSystem;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String sourceMessageId;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String sourceRequestId;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String sourceUrl;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String senderKey;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String receiverKey;
}
