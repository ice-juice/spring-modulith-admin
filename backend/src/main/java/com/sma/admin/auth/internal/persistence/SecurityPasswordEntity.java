package com.sma.admin.auth.internal.persistence;

import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("auth_security_password")
public class SecurityPasswordEntity extends TenantEntity {
    private Long userId;
    private String portal;
    private String passwordHash;
    private String pepperKeyId;
    private Long credentialVersion;
    private java.time.Instant changedAt;
}
