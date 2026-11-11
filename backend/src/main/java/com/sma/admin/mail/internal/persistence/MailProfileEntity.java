package com.sma.admin.mail.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("mail_profile")
public class MailProfileEntity extends TenantEntity {
    private String code;
    private String name;
    private Boolean enabled;
    private Long version;
    private Long configurationVersion;
    private Long credentialVersion;
    private java.time.LocalDate quotaDay;
    private Integer dayAttempts;
    private Long quotaMinute;
    private Integer minuteAttempts;
    private Integer pendingDeliveries;
    private String recipientKeyEnvelope;
}
