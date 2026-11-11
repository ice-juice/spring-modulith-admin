package com.sma.admin.tenant.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Platform directory metadata, only the platform-operator service may mutate it. */
@Getter @Setter @TableName("tenant_tenant")
public class TenantRecordEntity extends BaseEntity {
    private String code;
    private String name;
    private String status;
    private Long securityGeneration;
}
