package com.sma.admin.mail.internal.persistence;

import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("mail_attempt")
public class MailAttemptEntity extends TenantEntity {
    private Long deliveryId;
    private Long fence;
    private String state;
    private java.time.Instant startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private java.time.Instant finishedAt;
    private String resultCode;
}

