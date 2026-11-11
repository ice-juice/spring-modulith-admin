package com.sma.admin.tenant.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/** Insert-only published snapshot. Rollback creates another revision; it never overwrites this row. */
@Getter @Setter @TableName("tenant_brand_revision")
public class BrandRevisionEntity extends TenantEntity {
    private String portal;
    private Long revision;
    private Long sourceRevisionId;
    private String contentJson;
    private String contentHash;
    private String operationId;
    private String operationHash;
    private Long publisherId;
    private String defaultsRevision;
    private String legacyBackgroundUrl;
}
