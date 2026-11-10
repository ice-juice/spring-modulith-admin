package com.sma.admin.auth.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("auth_session")
public class SessionEntity extends TenantEntity {
    private Long userId; private String portal; private String tokenHash;
    private Instant expiresAt; private Instant lastSeenAt; private String ip; private String userAgent; private Boolean locked;
    private Long credentialVersion;
}
