package com.sma.admin.auth.internal.persistence;

import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("auth_challenge")
public class SecurityChallengeEntity extends TenantEntity {
    private Long userId;
    private String portal;
    private String tokenHash;
    private String purpose;
    private Long sourceSessionId;
    private Long accountCredentialVersion;
    private Long securityVersion;
    private Long tenantSecurityGeneration;
    private java.time.Instant expiresAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private java.time.Instant consumedAt;
    private Integer attemptCount;
    private Boolean singleTerminal;
    private Long sessionTtlSeconds;
}
