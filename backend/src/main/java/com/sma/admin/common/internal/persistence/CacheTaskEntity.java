package com.sma.admin.common.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("common_cache_task")
public class CacheTaskEntity extends TenantEntity {
    private String publicId;
    private String portal;
    private Long tenantGeneration;
    private String action;
    private String state;
    private String reason;
    private String frozenJson;
    private String nodeScopeJson;
    private Instant expiresAt;
    private Boolean cancelRequested;
    private Long fence;
    private String leaseOwner;
    private Instant leaseUntil;
    private Instant warmStartedAt;
}
