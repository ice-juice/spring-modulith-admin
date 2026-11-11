package com.sma.admin.mail.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("mail_template")
public class MailTemplateEntity extends TenantEntity {
    private String code;
    private String application;
    private String scene;
    private String language;
    private String profileCode;
    private String contentJson;
    private Boolean enabled;
    private Long version;
    private Long publishedVersion;
}
