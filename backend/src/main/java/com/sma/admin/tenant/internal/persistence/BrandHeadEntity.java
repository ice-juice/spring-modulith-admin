package com.sma.admin.tenant.internal.persistence;

import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("tenant_brand_head")
public class BrandHeadEntity extends TenantEntity {
    private String portal;
    private String draftJson;
    private Long draftVersion;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long publishedRevisionId;
    private Long nextRevision;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String legacyBackgroundUrl;
}
