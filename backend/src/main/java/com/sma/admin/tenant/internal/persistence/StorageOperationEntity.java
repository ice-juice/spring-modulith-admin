package com.sma.admin.tenant.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.sma.admin.common.persistence.TenantEntity;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Platform tenant owns the receipt; ownerTenantId is the target binding, never the actor identity. */
@Getter @Setter @TableName("tenant_storage_operation")
public class StorageOperationEntity extends TenantEntity {
    private String operationId;
    private Long bindingId;
    private Long ownerTenantId;
    private String storageGroup;
    private String resourceId;
    private Long resourceRevision;
    private Long expectedBindingVersion;
    private Long fence;
    private String state;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant leaseUntil;
    private Instant completedAt;
}
