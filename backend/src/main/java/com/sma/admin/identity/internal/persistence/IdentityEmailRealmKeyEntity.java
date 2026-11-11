package com.sma.admin.identity.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("identity_email_realm_key")
public class IdentityEmailRealmKeyEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long realmId;private String keyEnvelope;
}
