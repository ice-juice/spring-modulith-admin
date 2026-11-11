package com.sma.admin.auth.internal.persistence;

import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("auth_totp_credential")
public class TotpCredentialEntity extends TenantEntity {
    private Long userId;
    private String portal;
    private Boolean enabled;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String secretCiphertext;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long lastAcceptedStep;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private java.time.Instant enabledAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String pendingSecretCiphertext;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long pendingChallengeId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private java.time.Instant pendingExpiresAt;
}
