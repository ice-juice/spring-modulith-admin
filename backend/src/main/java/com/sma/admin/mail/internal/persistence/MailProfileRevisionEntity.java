package com.sma.admin.mail.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/** Immutable connection snapshot. Only the profile's current pointer/enable flag changes. */
@Getter @Setter @TableName("mail_profile_revision")
public class MailProfileRevisionEntity extends TenantEntity {
    private Long profileId;
    private Long configurationVersion;
    private Long credentialVersion;
    private String host;
    private Integer port;
    private String tlsMode;
    private String username;
    private String secretEnvelope;
    private String fromAddress;
    private String fromName;
    private String applicationsJson;
    private Integer dailyQuota;
    private Integer perMinuteLimit;
}
