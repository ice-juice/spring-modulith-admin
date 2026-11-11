package com.sma.admin.auth.internal.persistence;

import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("auth_throttle")
public class SecurityThrottleEntity extends TenantEntity {
    private String scope;
    private String subjectHash;
    private Integer attemptCount;
    private java.time.Instant expiresAt;
}
