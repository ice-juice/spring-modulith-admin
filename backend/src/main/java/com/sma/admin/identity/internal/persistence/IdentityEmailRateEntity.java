package com.sma.admin.identity.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("identity_email_rate")
public class IdentityEmailRateEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String scopeKey;private Long windowSlot;private Integer attempts;private java.time.Instant expiresAt;
}
