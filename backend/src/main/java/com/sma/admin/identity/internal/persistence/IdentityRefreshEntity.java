package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_refresh")
public class IdentityRefreshEntity extends TenantEntity {
    private Long sessionId;
    private String tokenHash;
    private String csrfHash;
    private java.time.Instant consumedAt;
    private java.time.Instant expiresAt;
}
