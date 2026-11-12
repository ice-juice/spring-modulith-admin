package com.sma.admin.tenant.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Global platform binding directory; business rows remain tenant isolated on their own resource. */
@Getter @Setter @TableName("tenant_storage_binding")
public class StorageBindingEntity extends BaseEntity {
    private Long ownerTenantId;
    private String storageGroup;
    private String resourceId;
    private Long resourceRevision;
    private String schemaCompatibility;
    private Long bindingVersion;
    private String state;
}
