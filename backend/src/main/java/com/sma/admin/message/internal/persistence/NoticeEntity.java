package com.sma.admin.message.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.*;
@Getter @Setter @TableName("message_notice")
public class NoticeEntity extends TenantEntity {
 private String portal;private Long deptId;private String title;private String content;private String type;private Integer priority;
 private String audienceType;private String audienceIds;
}
