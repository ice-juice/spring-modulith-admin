package com.sma.admin.tenant.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("tenant_brand_reference")
public class BrandReferenceEntity extends TenantEntity {
    private String portal;
    private String referenceKey;
    private String slot;
    private Long fileId;
}
