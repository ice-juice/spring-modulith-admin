package com.sma.admin.openapi.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.*;
@Getter @Setter @TableName("openapi_token")
public class TokenEntity extends TenantEntity {
 private Long applicationId;private String portal;private String tokenHash;private Long credentialVersion;private Instant expiresAt;
}
