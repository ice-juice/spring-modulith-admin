package com.sma.admin.openapi.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.*;
@Getter @Setter @TableName("openapi_nonce")
public class NonceEntity extends TenantEntity {private Long applicationId;private String nonce;private Instant expiresAt;}
