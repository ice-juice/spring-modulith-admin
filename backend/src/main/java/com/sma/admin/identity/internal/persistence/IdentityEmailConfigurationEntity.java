package com.sma.admin.identity.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("identity_email_configuration")
public class IdentityEmailConfigurationEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long clientId;private String language;private Boolean enabled;private Long version;
    private String registrationTemplate;private String resetTemplate;private String bindingTemplate;
}
