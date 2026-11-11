package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_login_rate")
public class IdentityLoginRateEntity extends TenantEntity {
    private String scopeKey;
    private Long minuteSlot;
    private Integer starts;
    private java.time.Instant expiresAt;
}
