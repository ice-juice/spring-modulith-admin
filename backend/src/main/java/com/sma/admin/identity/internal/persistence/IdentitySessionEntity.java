package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_session")
public class IdentitySessionEntity extends TenantEntity {
    private String authenticationMethod;
    private java.time.Instant authenticatedAt;
    private Long subjectId;
    private Long realmId;
    private Long clientId;
    private String application;
    private String mode;
    private String status;
    private String accessHash;
    private String csrfHash;
    private java.time.Instant accessExpiresAt;
    private java.time.Instant refreshExpiresAt;
    private Long tenantGeneration;
    private Long realmGeneration;
    private Long clientGeneration;
    private Long subjectGeneration;
    private java.time.Instant revokedAt;
    private String revocationReason;
}
