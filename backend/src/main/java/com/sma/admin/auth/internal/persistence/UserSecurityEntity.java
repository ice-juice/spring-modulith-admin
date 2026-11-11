package com.sma.admin.auth.internal.persistence;

import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("auth_user_security")
public class UserSecurityEntity extends TenantEntity {
    private Long userId;
    private String portal;
    private Long securityVersion;
}
