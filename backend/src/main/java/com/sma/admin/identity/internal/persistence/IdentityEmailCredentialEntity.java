package com.sma.admin.identity.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("identity_email_credential")
public class IdentityEmailCredentialEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long realmId;private Long subjectId;private String emailIndex;private String emailEnvelope;private Long emailVersion;
    private java.time.Instant emailVerifiedAt;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS) private String passwordHash;
    private Long passwordVersion;
}
