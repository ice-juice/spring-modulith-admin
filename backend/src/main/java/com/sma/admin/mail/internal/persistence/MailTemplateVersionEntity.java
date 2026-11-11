package com.sma.admin.mail.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("mail_template_version")
public class MailTemplateVersionEntity extends TenantEntity {
    private Long templateId;
    private Long version;
    private Long profileId;
    private Long profileVersion;
    private Long credentialVersion;
    private String contentJson;
}
