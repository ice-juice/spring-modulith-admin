package com.sma.admin.mail.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("mail_suppression")
public class MailSuppressionEntity extends TenantEntity {
    private Long profileId;
    private String application;
    private String recipientKey;
    private String reason;
}
