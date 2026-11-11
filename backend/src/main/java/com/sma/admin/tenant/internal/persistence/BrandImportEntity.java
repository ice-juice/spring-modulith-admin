package com.sma.admin.tenant.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("tenant_brand_import")
public class BrandImportEntity extends TenantEntity {
    private String sourceRevision;
    private String portalsJson;
    private String excludedKeysJson;
}
