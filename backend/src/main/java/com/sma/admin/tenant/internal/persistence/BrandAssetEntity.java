package com.sma.admin.tenant.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("tenant_brand_asset")
public class BrandAssetEntity extends TenantEntity {
    private String portal;
    private Long revisionId;
    private String slot;
    private Long sourceFileId;
    private byte[] imageBytes;
    private String checksum;
    private Integer width;
    private Integer height;
}
