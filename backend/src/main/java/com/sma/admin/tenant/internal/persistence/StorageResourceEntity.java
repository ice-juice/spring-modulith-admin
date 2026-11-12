package com.sma.admin.tenant.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Platform directory metadata; never holds credentials. */
@Getter @Setter @TableName("tenant_storage_resource")
public class StorageResourceEntity extends BaseEntity {
    private String resourceId;
    private Long revision;
    private String displayName;
    private String dbHost;
    private Integer dbPort;
    private String databaseName;
    private String tlsMode;
    private String secretRef;
    private Integer poolMax;
    private Integer connectTimeoutMillis;
    private String state;
}
