package com.sma.admin.identity.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("identity_reauthentication")
public class IdentityReauthenticationEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long realmId;private Long subjectId;private Long sessionId;private String stampJson;private String action;private String targetRef;private String method;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS) private String grantHash;
    private String status;private java.time.Instant expiresAt;private java.time.Instant retainUntil;private java.time.Instant consumedAt;
}
